package com.scaffy.backend.init.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.scaffy.backend.init.InitRequest;
import com.scaffy.backend.init.StackCatalog;

/**
 * Orchestrates project generation:
 * <ol>
 *   <li>Picks cached framework artifacts (Angular, Spring Boot) and substitutes
 *       {@code __SCAFFY_*__} tokens via {@link ArtifactComposer}.</li>
 *   <li>Renders the Scaffy-owned overlay (CI workflows, root README, root
 *       .gitignore) via {@link TemplateOverlay} with {@code {{var}}}
 *       substitution.</li>
 *   <li>Concatenates both streams and hands them to {@link ZipBuilder}.</li>
 * </ol>
 */
@Component
public class ProjectGenerator {

	private static final String GROUP_ID = "com.example";

	private final ArtifactComposer artifactComposer;
	private final TemplateOverlay templateOverlay;
	private final ZipBuilder zipBuilder;

	public ProjectGenerator(ArtifactComposer artifactComposer,
							TemplateOverlay templateOverlay,
							ZipBuilder zipBuilder) {
		this.artifactComposer = artifactComposer;
		this.templateOverlay = templateOverlay;
		this.zipBuilder = zipBuilder;
	}

	public byte[] generate(InitRequest request) throws IOException {
		Map<String, String> tokens = buildArtifactTokens(request);
		Map<String, String> templateVars = buildTemplateVariables(request);

		List<EmittedFile> files = new ArrayList<>();
		files.addAll(composeBackend(request, tokens));
		files.addAll(composeFrontend(request, tokens));
		files.addAll(emitOverlay(request, templateVars));

		return zipBuilder.build(request.projectName(), files);
	}

	// ------------------------------------------------------------------
	// Token + variable maps
	// ------------------------------------------------------------------

	private Map<String, String> buildArtifactTokens(InitRequest request) {
		String kebab = request.projectName();
		String pascal = pascalize(kebab);
		String camel = decapitalize(pascal);
		String packageLeaf = kebab.replace("-", "");
		String packageName = GROUP_ID + "." + packageLeaf;
		String packageDir = packageName.replace('.', '/');

		Map<String, String> tokens = new HashMap<>();
		tokens.put("__SCAFFY_PROJECT_NAME__", kebab);
		tokens.put("__SCAFFY_PROJECT_PASCAL__", pascal);
		tokens.put("__SCAFFY_PROJECT_CAMEL__", camel);
		tokens.put("__SCAFFY_PACKAGE__", packageName);
		tokens.put("__SCAFFY_PACKAGE_DIR__", packageDir);
		return tokens;
	}

	private Map<String, String> buildTemplateVariables(InitRequest request) {
		Map<String, String> vars = new HashMap<>();
		vars.put("projectName", request.projectName());
		if (StackCatalog.FRONTEND_VUE.equals(request.frontend())) {
			vars.put("frontendLabel", "Vue application");
			vars.put("frontendDevCmd", "npm run dev");
			vars.put("frontendPort", "5173");
			vars.put("frontendDistPath", "dist");
		} else {
			vars.put("frontendLabel", "Angular application");
			vars.put("frontendDevCmd", "npm start");
			vars.put("frontendPort", "4200");
			vars.put("frontendDistPath", "dist/" + request.projectName() + "/browser");
		}
		return vars;
	}

	// ------------------------------------------------------------------
	// Sources
	// ------------------------------------------------------------------

	private List<EmittedFile> composeBackend(InitRequest request, Map<String, String> tokens) throws IOException {
		if (StackCatalog.BACKEND_SPRING_BOOT.equals(request.backend())) {
			return artifactComposer.compose("artifacts/spring-boot.zip", "backend", tokens);
		}
		return List.of();
	}

	private List<EmittedFile> composeFrontend(InitRequest request, Map<String, String> tokens) throws IOException {
		if (StackCatalog.FRONTEND_ANGULAR.equals(request.frontend())) {
			return artifactComposer.compose("artifacts/angular.zip", "frontend", tokens);
		}
		if (StackCatalog.FRONTEND_VUE.equals(request.frontend())) {
			return artifactComposer.compose("artifacts/vue.zip", "frontend", tokens);
		}
		return List.of();
	}

	private List<EmittedFile> emitOverlay(InitRequest request, Map<String, String> vars) throws IOException {
		List<TemplateFile> templates = new ArrayList<>();
		templates.add(TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"));
		templates.add(TemplateFile.copy("templates/root/gitignore", ".gitignore"));
		if (Boolean.TRUE.equals(request.includeDocker())) {
			templates.add(TemplateFile.rendered("templates/docker/Dockerfile.backend.tmpl", "backend/Dockerfile"));
			templates.add(TemplateFile.rendered("templates/docker/Dockerfile.frontend.tmpl", "frontend/Dockerfile"));
			templates.add(TemplateFile.rendered("templates/docker/docker-compose.yml.tmpl", "docker-compose.yml"));
		}
		switch (request.pipeline()) {
			case StackCatalog.PIPELINE_GITHUB_ACTIONS -> templates.add(TemplateFile.rendered(
					"templates/pipeline/github-actions/ci.yml.tmpl",
					".github/workflows/ci.yml"));
			case StackCatalog.PIPELINE_GITLAB_CI -> templates.add(TemplateFile.rendered(
					"templates/pipeline/gitlab-ci/gitlab-ci.yml.tmpl",
					".gitlab-ci.yml"));
			default -> { /* validated upstream */ }
		}
		return templateOverlay.emit(templates, vars);
	}

	// ------------------------------------------------------------------
	// Naming helpers
	// ------------------------------------------------------------------

	/** "demo-app" → "DemoApp", matching what Angular CLI / Spring Initializr generate. */
	private static String pascalize(String kebab) {
		StringBuilder out = new StringBuilder(kebab.length());
		boolean upper = true;
		for (char c : kebab.toCharArray()) {
			if (c == '-' || c == '_') {
				upper = true;
			} else if (upper) {
				out.append(Character.toUpperCase(c));
				upper = false;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static String decapitalize(String pascal) {
		if (pascal.isEmpty()) {
			return pascal;
		}
		return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
	}
}
