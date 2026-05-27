package com.scaffy.backend.init;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Iteration 1: only Angular + Spring Boot is supported. Extending the catalog
 * means adding entries here; nothing else in the request flow needs to change.
 */
@Component
public class StackCatalog {

	public static final String FRONTEND_ANGULAR = "angular";
	public static final String FRONTEND_VUE = "vue";
	public static final String FRONTEND_REACT = "react";
	public static final String BACKEND_SPRING_BOOT = "spring-boot";
	public static final String BACKEND_DOTNET = "dotnet";
	public static final String BACKEND_NESTJS = "nestjs";
	public static final String PIPELINE_GITHUB_ACTIONS = "github-actions";
	public static final String PIPELINE_GITLAB_CI = "gitlab-ci";
	public static final String MATURITY_L1 = "l1";
	public static final String MATURITY_L2 = "l2";
	public static final String MATURITY_L3 = "l3";
	public static final String MATURITY_L4 = "l4";

	private static final Set<String> FRONTENDS = Set.of(FRONTEND_ANGULAR, FRONTEND_VUE, FRONTEND_REACT);
	private static final Set<String> BACKENDS = Set.of(BACKEND_SPRING_BOOT, BACKEND_DOTNET, BACKEND_NESTJS);
	private static final Set<String> PIPELINES = Set.of(PIPELINE_GITHUB_ACTIONS, PIPELINE_GITLAB_CI);
	private static final Set<String> MATURITY_LEVELS = Set.of(MATURITY_L1, MATURITY_L2, MATURITY_L3, MATURITY_L4);
	private static final Set<String> NODE_LTS = Set.of("20", "22", "24");
	private static final Set<String> JAVA_LTS = Set.of("17", "21", "25");
	private static final InitCatalogResponse CATALOG = new InitCatalogResponse(
			List.of(
					stack(
							FRONTEND_REACT,
							"React",
							"Vite React app with TypeScript, tests, Docker, and CI runtime overlays.",
							"19",
							version("18", "React 18", "18", "node-20", node("20"), node("22"), node("24")),
							version("19", "React 19", "19", "node-22", node("20"), node("22"), node("24"))),
					stack(
							FRONTEND_VUE,
							"Vue",
							"Vue 3 TypeScript app with Vite and selected Node runtime presets.",
							"3",
							version("3", "Vue 3.x", "3.x", "node-22", node("20"), node("22"), node("24"))),
					stack(
							FRONTEND_ANGULAR,
							"Angular",
							"Angular app generated against supported Angular and Node compatibility presets.",
							"20",
							version("18", "Angular 18", "18", "node-20", node("20"), node("22")),
							version("19", "Angular 19", "19", "node-22", node("20"), node("22")),
							version("20", "Angular 20", "20", "node-22", node("20"), node("22"), node("24")))),
			List.of(
					stack(
							BACKEND_SPRING_BOOT,
							"Spring Boot",
							"Spring Boot API with Maven, Docker, and Java-aware CI templates.",
							"4.0",
							version("3.5", "Spring Boot 3.5", "3.5", "java-21", java("17"), java("21")),
							version("4.0", "Spring Boot 4.0", "4.0", "java-21", java("21"), java("25"))),
					stack(
							BACKEND_NESTJS,
							"NestJS",
							"NestJS API generated with selected framework and Node runtime presets.",
							"11",
							version("10", "NestJS 10", "10", "node-22", node("20"), node("22")),
							version("11", "NestJS 11", "11", "node-22", node("22"), node("24"))),
					stack(
							BACKEND_DOTNET,
							".NET",
							".NET Web API with target framework, Docker, and CI presets.",
							"10",
							version("8", ".NET 8", "8", "dotnet-8", dotnet("8")),
							version("9", ".NET 9", "9", "dotnet-9", dotnet("9")),
							version("10", ".NET 10", "10", "dotnet-10", dotnet("10")))),
			List.of(
					new InitCatalogResponse.PipelineOption(
							PIPELINE_GITHUB_ACTIONS,
							"GitHub Actions",
							"Creates a GitHub Actions workflow using the selected runtime versions."),
					new InitCatalogResponse.PipelineOption(
							PIPELINE_GITLAB_CI,
							"GitLab CI",
							"Creates a GitLab pipeline using the selected runtime versions.")),
			List.of(
					maturity(
							MATURITY_L1,
							"L1 Initial / Chaos",
							"Small CI surface for prototypes: build validation and selected runtimes.",
							1,
							false),
					maturity(
							MATURITY_L2,
							"L2 Basic CI",
							"Build, test, deterministic installs, named steps, and artifact output.",
							2,
							false),
					maturity(
							MATURITY_L3,
							"L3 Structured Delivery",
							"Adds Dockerfiles, docker-compose, image build validation, cache, and versioned artifacts.",
							3,
							true),
					maturity(
							MATURITY_L4,
							"L4 Governed Automation",
							"Adds security scanning and deployment placeholders gated for real environments.",
							4,
							true)));

	public Set<String> frontends() { return FRONTENDS; }
	public Set<String> backends() { return BACKENDS; }
	public Set<String> pipelines() { return PIPELINES; }
	public Set<String> maturityLevels() { return MATURITY_LEVELS; }
	public InitCatalogResponse response() { return CATALOG; }

	public boolean supportsFrontend(String value) { return FRONTENDS.contains(value); }
	public boolean supportsBackend(String value) { return BACKENDS.contains(value); }
	public boolean supportsPipeline(String value) { return PIPELINES.contains(value); }
	public boolean supportsMaturityLevel(String value) { return MATURITY_LEVELS.contains(value); }

	public InitSelection selectionFor(InitJobRequest request) {
		InitCatalogResponse.StackOption frontend = findStack(CATALOG.frontends(), request.frontend(), "Frontend");
		InitCatalogResponse.VersionPreset frontendVersion = findVersion(
				frontend,
				request.frontendVersion(),
				"Frontend version");
		InitCatalogResponse.RuntimePreset frontendRuntime = findRuntime(
				frontendVersion,
				request.frontendRuntime(),
				"Frontend runtime");

		InitCatalogResponse.StackOption backend = findStack(CATALOG.backends(), request.backend(), "Backend");
		InitCatalogResponse.VersionPreset backendVersion = findVersion(
				backend,
				request.backendVersion(),
				"Backend version");
		InitCatalogResponse.RuntimePreset backendRuntime = findRuntime(
				backendVersion,
				request.backendRuntime(),
				"Backend runtime");

		InitCatalogResponse.PipelineOption pipeline = CATALOG.pipelines().stream()
				.filter(option -> option.id().equals(request.pipeline()))
				.findFirst()
				.orElseThrow(() -> new UnsupportedStackException(
						"Pipeline '" + request.pipeline() + "' is not supported."));

		InitCatalogResponse.MaturityPreset maturity = CATALOG.maturityPresets().stream()
				.filter(option -> option.id().equals(normalizeMaturity(request.pipelineMaturity())))
				.findFirst()
				.orElseThrow(() -> new UnsupportedStackException(
						"Pipeline maturity '" + request.pipelineMaturity() + "' is not supported."));
		boolean includeDocker = request.includeDocker() || maturity.dockerRequired();

		return new InitSelection(
				new InitSelection.SelectedStack(
						frontend.id(),
						frontend.name(),
						frontendVersion.id(),
						frontendVersion.label(),
						frontendVersion.version(),
						frontendRuntime.id(),
						frontendRuntime.label(),
						frontendRuntime.runtime(),
						frontendRuntime.version()),
				new InitSelection.SelectedStack(
						backend.id(),
						backend.name(),
						backendVersion.id(),
						backendVersion.label(),
						backendVersion.version(),
						backendRuntime.id(),
						backendRuntime.label(),
						backendRuntime.runtime(),
						backendRuntime.version()),
				new InitSelection.SelectedPipeline(pipeline.id(), pipeline.name()),
				new InitSelection.SelectedMaturity(
						maturity.id(),
						maturity.label(),
						maturity.description(),
						maturity.level(),
						maturity.dockerRequired()),
				includeDocker);
	}

	private static String normalizeMaturity(String value) {
		if (value == null || value.isBlank()) {
			return MATURITY_L2;
		}
		return value;
	}

	private static InitCatalogResponse.StackOption findStack(
			List<InitCatalogResponse.StackOption> options,
			String id,
			String label) {
		return options.stream()
				.filter(option -> option.id().equals(id))
				.findFirst()
				.orElseThrow(() -> new UnsupportedStackException(label + " '" + id + "' is not supported."));
	}

	private static InitCatalogResponse.VersionPreset findVersion(
			InitCatalogResponse.StackOption stack,
			String versionId,
			String label) {
		return stack.versions().stream()
				.filter(version -> version.id().equals(versionId))
				.findFirst()
				.orElseThrow(() -> new UnsupportedStackException(
						label + " '" + versionId + "' is not supported for " + stack.name() + "."));
	}

	private static InitCatalogResponse.RuntimePreset findRuntime(
			InitCatalogResponse.VersionPreset version,
			String runtimeId,
			String label) {
		return version.runtimes().stream()
				.filter(runtime -> runtime.id().equals(runtimeId))
				.findFirst()
				.orElseThrow(() -> new UnsupportedStackException(
						label + " '" + runtimeId + "' is not supported for " + version.label() + "."));
	}

	private static InitCatalogResponse.StackOption stack(
			String id,
			String name,
			String description,
			String defaultVersionId,
			InitCatalogResponse.VersionPreset... versions) {
		return new InitCatalogResponse.StackOption(
				id,
				name,
				description,
				defaultVersionId,
				List.of(versions));
	}

	private static InitCatalogResponse.VersionPreset version(
			String id,
			String label,
			String version,
			String defaultRuntimeId,
			InitCatalogResponse.RuntimePreset... runtimes) {
		return new InitCatalogResponse.VersionPreset(
				id,
				label,
				version,
				defaultRuntimeId,
				List.of(runtimes));
	}

	private static InitCatalogResponse.RuntimePreset node(String version) {
		return new InitCatalogResponse.RuntimePreset(
				"node-" + version,
				"Node " + version,
				"node",
				version,
				NODE_LTS.contains(version));
	}

	private static InitCatalogResponse.RuntimePreset java(String version) {
		return new InitCatalogResponse.RuntimePreset(
				"java-" + version,
				"Java " + version,
				"java",
				version,
				JAVA_LTS.contains(version));
	}

	private static InitCatalogResponse.RuntimePreset dotnet(String version) {
		return new InitCatalogResponse.RuntimePreset(
				"dotnet-" + version,
				".NET " + version,
				"dotnet",
				version,
				isDotnetLts(version));
	}

	private static InitCatalogResponse.MaturityPreset maturity(
			String id,
			String label,
			String description,
			int level,
			boolean dockerRequired) {
		return new InitCatalogResponse.MaturityPreset(id, label, description, level, dockerRequired);
	}

	private static boolean isDotnetLts(String version) {
		try {
			return Integer.parseInt(version) % 2 == 0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}
}
