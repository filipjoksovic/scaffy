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
	private static final String TEMPLATE_VAR_FRONTEND_LABEL = "frontendLabel";
	private static final String TEMPLATE_VAR_FRONTEND_DEV_CMD = "frontendDevCmd";
	private static final String TEMPLATE_VAR_FRONTEND_PORT = "frontendPort";
	private static final String TEMPLATE_VAR_FRONTEND_DIST_PATH = "frontendDistPath";
	private static final String TEMPLATE_ROOT_FRONTEND = "frontend";
	private static final String TEMPLATE_VAR_BACKEND_LABEL = "backendLabel";
	private static final String TEMPLATE_VAR_BACKEND_RUN_CMD = "backendRunCmd";
	private static final String TEMPLATE_VAR_BACKEND_PORT = "backendPort";
	private static final String TEMPLATE_ROOT_BACKEND = "backend";

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
		vars.put("projectPascal", pascalize(request.projectName()));
		switch (request.frontend()) {
			case StackCatalog.FRONTEND_VUE -> {
				vars.put(TEMPLATE_VAR_FRONTEND_LABEL, "Vue application");
				vars.put(TEMPLATE_VAR_FRONTEND_DEV_CMD, "npm run dev");
				vars.put(TEMPLATE_VAR_FRONTEND_PORT, "5173");
				vars.put(TEMPLATE_VAR_FRONTEND_DIST_PATH, "dist");
			}
			case StackCatalog.FRONTEND_REACT -> {
				vars.put(TEMPLATE_VAR_FRONTEND_LABEL, "React application");
				vars.put(TEMPLATE_VAR_FRONTEND_DEV_CMD, "npm run dev");
				vars.put(TEMPLATE_VAR_FRONTEND_PORT, "5173");
				vars.put(TEMPLATE_VAR_FRONTEND_DIST_PATH, "dist");
			}
			default -> {
				vars.put(TEMPLATE_VAR_FRONTEND_LABEL, "Angular application");
				vars.put(TEMPLATE_VAR_FRONTEND_DEV_CMD, "npm start");
				vars.put(TEMPLATE_VAR_FRONTEND_PORT, "4200");
				vars.put(TEMPLATE_VAR_FRONTEND_DIST_PATH, "dist/" + request.projectName() + "/browser");
			}
		}
		switch (request.backend()) {
			case StackCatalog.BACKEND_DOTNET -> {
				vars.put(TEMPLATE_VAR_BACKEND_LABEL, ".NET Web API");
				vars.put(TEMPLATE_VAR_BACKEND_RUN_CMD, "dotnet run");
				vars.put(TEMPLATE_VAR_BACKEND_PORT, "8080");
			}
			case StackCatalog.BACKEND_NESTJS -> {
				vars.put(TEMPLATE_VAR_BACKEND_LABEL, "NestJS service");
				vars.put(TEMPLATE_VAR_BACKEND_RUN_CMD, "npm install\nnpm run start:dev");
				vars.put(TEMPLATE_VAR_BACKEND_PORT, "3000");
			}
			default -> {
				vars.put(TEMPLATE_VAR_BACKEND_LABEL, "Spring Boot service (Java 25)");
				vars.put(TEMPLATE_VAR_BACKEND_RUN_CMD, "mvn spring-boot:run");
				vars.put(TEMPLATE_VAR_BACKEND_PORT, "8080");
			}
		}
		return vars;
	}

	// ------------------------------------------------------------------
	// Sources
	// ------------------------------------------------------------------

	private List<EmittedFile> composeBackend(InitRequest request, Map<String, String> tokens) throws IOException {
		return switch (request.backend()) {
			case StackCatalog.BACKEND_SPRING_BOOT -> artifactComposer.compose("artifacts/spring-boot.zip", TEMPLATE_ROOT_BACKEND, tokens);
			case StackCatalog.BACKEND_DOTNET      -> artifactComposer.compose("artifacts/dotnet.zip", TEMPLATE_ROOT_BACKEND, tokens);
			case StackCatalog.BACKEND_NESTJS      -> artifactComposer.compose("artifacts/nestjs.zip", TEMPLATE_ROOT_BACKEND, tokens);
			default -> List.of();
		};
	}

	private List<EmittedFile> composeFrontend(InitRequest request, Map<String, String> tokens) throws IOException {
		if (StackCatalog.FRONTEND_ANGULAR.equals(request.frontend())) {
			return artifactComposer.compose("artifacts/angular.zip", TEMPLATE_ROOT_FRONTEND, tokens);
		}
		if (StackCatalog.FRONTEND_VUE.equals(request.frontend())) {
			return artifactComposer.compose("artifacts/vue.zip", TEMPLATE_ROOT_FRONTEND, tokens);
		}
		if (StackCatalog.FRONTEND_REACT.equals(request.frontend())) {
			return artifactComposer.compose("artifacts/react.zip", TEMPLATE_ROOT_FRONTEND, tokens);
		}
		return List.of();
	}

	private List<EmittedFile> emitOverlay(InitRequest request, Map<String, String> vars) throws IOException {
		String backendSuffix = switch (request.backend()) {
			case StackCatalog.BACKEND_DOTNET -> ".dotnet";
			case StackCatalog.BACKEND_NESTJS -> ".nestjs";
			default -> "";
		};
		List<TemplateFile> templates = new ArrayList<>();
		templates.add(TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"));
		templates.add(TemplateFile.copy("templates/root/gitignore", ".gitignore"));
		if (Boolean.TRUE.equals(request.includeDocker())) {
			templates.add(TemplateFile.rendered(
					"templates/docker/Dockerfile.backend" + backendSuffix + ".tmpl", "backend/Dockerfile"));
			templates.add(TemplateFile.rendered("templates/docker/Dockerfile.frontend.tmpl", "frontend/Dockerfile"));
			templates.add(TemplateFile.rendered("templates/docker/docker-compose.yml.tmpl", "docker-compose.yml"));
		}
		switch (request.pipeline()) {
			case StackCatalog.PIPELINE_GITHUB_ACTIONS -> templates.add(TemplateFile.rendered(
					"templates/pipeline/github-actions/ci" + backendSuffix + ".yml.tmpl",
					".github/workflows/ci.yml"));
			case StackCatalog.PIPELINE_GITLAB_CI -> templates.add(TemplateFile.rendered(
					"templates/pipeline/gitlab-ci/gitlab-ci" + backendSuffix + ".yml.tmpl",
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
