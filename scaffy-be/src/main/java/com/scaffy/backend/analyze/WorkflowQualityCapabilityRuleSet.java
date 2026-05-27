package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Detects workflow-hygiene smells defined by Khatami et al. (GHA configuration smells)
 * and selected Zampetti CI/CD smells. Contributes findings to the {@code workflow_quality}
 * dimension alongside {@link CodeAnalysisCapabilityRuleSet} and {@link NotificationCapabilityRuleSet}.
 *
 * <p>Spec source: Analizator_v1 (Workflow Quality &amp; Optimization), capabilities:
 * Execution safety, Selective execution, Maintainability, Reproducibility, Matrix / cache.
 */
@Component
@Order(24)
public class WorkflowQualityCapabilityRuleSet implements CapabilityRuleSet {

	private static final String DIMENSION = "workflow_quality";

	private static final String CAPABILITY_EXECUTION_SAFETY = "Execution safety";
	private static final String CAPABILITY_SELECTIVE_EXECUTION = "Selective execution";
	private static final String CAPABILITY_MAINTAINABILITY = "Maintainability";
	private static final String CAPABILITY_REPRODUCIBILITY = "Reproducibility";
	private static final String CAPABILITY_MATRIX_CACHE = "Matrix / cache optimization";

	private static final Pattern PINNED_ACTION_SHA = Pattern.compile("@[0-9a-f]{40}");
	private static final Pattern UNPINNED_ACTION_TAG = Pattern.compile("@v?+\\d++(?:\\.\\d++){0,4}+$");
	private static final Pattern DEFAULT_JOB_NAME = Pattern.compile("^(?:job|step)\\d*+$|^(?:build|test|deploy|job1)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern UNPINNED_NPM_INSTALL = Pattern.compile(
			"\\bnpm\\s++install\\s++[^@\\s]\\S*+(?<!@\\d)", Pattern.CASE_INSENSITIVE);
	private static final Pattern UNPINNED_PIP_INSTALL = Pattern.compile(
			"\\bpip\\s++install\\s++[^=\\s<>]\\S*+$", Pattern.CASE_INSENSITIVE);
	private static final Pattern RUNS_ON_LATEST = Pattern.compile(
			"runs-on[\\s:=]++[a-z0-9]{1,16}-latest\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern MULTI_OS_MATRIX = Pattern.compile(
			"\\bos[\\s:=]++\\[[^\\],]++,", Pattern.CASE_INSENSITIVE);
	private static final Pattern MULTI_VERSION_MATRIX = Pattern.compile(
			"\\b(?:node-version|python-version|java-version|go-version|dotnet-version|ruby-version)[\\s:=]++\\[[^\\],]++,",
			Pattern.CASE_INSENSITIVE);
	private static final Set<String> CACHE_ACTION_PREFIXES = Set.of("actions/cache", "actions/setup-node", "actions/setup-java",
			"actions/setup-python", "actions/setup-go", "actions/setup-dotnet");

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		findings.addAll(executionSafety(document));
		findings.addAll(selectiveExecution(document));
		findings.addAll(maintainability(document));
		findings.addAll(reproducibility(document));
		findings.addAll(matrixCache(document));

		return findings;
	}

	private List<CapabilityFinding> executionSafety(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		PipelineJob firstJob = document.jobs().stream().findFirst().orElse(null);
		String anyLocation = firstJob != null ? firstJob.location() : "workflow";

		boolean allJobsHaveTimeout = !document.jobs().isEmpty()
				&& document.jobs().stream().allMatch(job -> hasText(job.details())
						&& job.details().toLowerCase(Locale.ROOT).contains("timeout-minutes:"));
		if (allJobsHaveTimeout) {
			findings.add(CapabilityFinding.positive("TIMEOUT_PRESENT", DIMENSION, CAPABILITY_EXECUTION_SAFETY,
					"timeout-minutes set on all jobs", anyLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("MISSING_TIMEOUT", DIMENSION, CAPABILITY_EXECUTION_SAFETY,
					"at least one job has no timeout-minutes", anyLocation));
		}

		boolean concurrencyPresent = document.jobs().stream()
				.anyMatch(job -> hasText(job.details())
						&& job.details().toLowerCase(Locale.ROOT).contains("concurrency:"));
		if (concurrencyPresent) {
			findings.add(CapabilityFinding.positive("CONCURRENCY_CONTROL_PRESENT", DIMENSION, CAPABILITY_EXECUTION_SAFETY,
					"concurrency: configured", anyLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("MISSING_CONCURRENCY_CONTROL", DIMENSION, CAPABILITY_EXECUTION_SAFETY,
					"no concurrency: control detected", anyLocation));
		}

		document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.filter(step -> hasText(step.details())
						&& step.details().toLowerCase(Locale.ROOT).replace(" ", "").contains("continue-on-error:true"))
				.findFirst()
				.ifPresent(step -> findings.add(CapabilityFinding.smell("CONTINUE_ON_ERROR_USED", DIMENSION,
						CAPABILITY_EXECUTION_SAFETY, "continue-on-error: true", fieldLocation(step.location(), "continue-on-error"))));

		return findings;
	}

	private List<CapabilityFinding> selectiveExecution(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		PipelineTrigger firstTrigger = document.triggers().stream().findFirst().orElse(null);
		String triggerLocation = firstTrigger != null ? firstTrigger.location() : "workflow.on";

		boolean pathFiltersPresent = document.jobs().stream()
				.anyMatch(job -> hasText(job.details())
						&& (job.details().toLowerCase(Locale.ROOT).contains("paths:")
								|| job.details().toLowerCase(Locale.ROOT).contains("paths-ignore:")));
		if (pathFiltersPresent) {
			findings.add(CapabilityFinding.positive("PATH_FILTERS_PRESENT", DIMENSION, CAPABILITY_SELECTIVE_EXECUTION,
					"paths filter configured", triggerLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("NO_PATH_FILTERS", DIMENSION, CAPABILITY_SELECTIVE_EXECUTION,
					"no on.push.paths or on.pull_request.paths filter", triggerLocation));
		}

		boolean scheduledTrigger = document.triggers().stream()
				.anyMatch(t -> "schedule".equalsIgnoreCase(t.name()) || "cron".equalsIgnoreCase(t.name()));
		boolean forkGuard = document.jobs().stream()
				.anyMatch(job -> hasText(job.condition())
						&& job.condition().toLowerCase(Locale.ROOT).contains("github.repository_owner"));
		if (scheduledTrigger && !forkGuard) {
			findings.add(CapabilityFinding.smell("SCHEDULED_RUN_ON_FORKS", DIMENSION, CAPABILITY_SELECTIVE_EXECUTION,
					"schedule trigger without fork owner check", triggerLocation));
		}

		boolean uploadOnForkUnguarded = document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.anyMatch(step -> step.uses() != null
						&& step.uses().toLowerCase(Locale.ROOT).startsWith("actions/upload-artifact")
						&& (step.condition() == null
								|| !step.condition().toLowerCase(Locale.ROOT).contains("github.repository_owner")));
		if (uploadOnForkUnguarded) {
			findings.add(CapabilityFinding.smell("UPLOAD_ARTIFACT_ON_FORKS", DIMENSION, CAPABILITY_SELECTIVE_EXECUTION,
					"actions/upload-artifact without fork owner guard", triggerLocation));
		}

		return findings;
	}

	private List<CapabilityFinding> maintainability(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		List<PipelineStep> runSteps = document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.filter(step -> hasText(step.command()))
				.toList();

		boolean anyUnnamedRunStep = runSteps.stream().anyMatch(step -> !hasText(step.name()));
		if (!runSteps.isEmpty()) {
			if (anyUnnamedRunStep) {
				PipelineStep firstUnnamed = runSteps.stream().filter(step -> !hasText(step.name())).findFirst().orElseThrow();
				findings.add(CapabilityFinding.smell("UNNAMED_RUN_STEP", DIMENSION, CAPABILITY_MAINTAINABILITY,
						"run step without name:", firstUnnamed.location()));
			}
			else {
				findings.add(CapabilityFinding.positive("NAMED_RUN_STEPS_PRESENT", DIMENSION, CAPABILITY_MAINTAINABILITY,
						"all run steps have name:", runSteps.get(0).location()));
			}
		}

		document.jobs().stream()
				.filter(job -> hasText(job.id()) && DEFAULT_JOB_NAME.matcher(job.id()).matches())
				.findFirst()
				.ifPresent(job -> findings.add(CapabilityFinding.smell("DEFAULT_JOB_NAME", DIMENSION,
						CAPABILITY_MAINTAINABILITY, "default-style job name: " + job.id(), job.location())));

		runSteps.stream()
				.filter(step -> step.command().trim().split("\\r?\\n").length > 1
						&& !step.command().trim().endsWith("\\"))
				.findFirst()
				.ifPresent(step -> findings.add(CapabilityFinding.smell("MULTI_COMMAND_STEP", DIMENSION,
						CAPABILITY_MAINTAINABILITY, "multi-line shell block in one step", step.location())));

		return findings;
	}

	private List<CapabilityFinding> reproducibility(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		boolean anyLatestRunner = document.jobs().stream()
				.anyMatch(job -> hasText(job.details()) && RUNS_ON_LATEST.matcher(job.details()).find());
		boolean anyRunsOn = document.jobs().stream()
				.anyMatch(job -> hasText(job.details())
						&& job.details().toLowerCase(Locale.ROOT).contains("runs-on"));
		if (anyLatestRunner) {
			PipelineJob job = document.jobs().stream()
					.filter(j -> hasText(j.details()) && RUNS_ON_LATEST.matcher(j.details()).find())
					.findFirst()
					.orElseThrow();
			findings.add(CapabilityFinding.smell("RUNS_ON_LATEST", DIMENSION, CAPABILITY_REPRODUCIBILITY,
					"runs-on uses a -latest tag", job.location()));
		}
		else if (anyRunsOn) {
			findings.add(CapabilityFinding.positive("PINNED_RUNNER_PRESENT", DIMENSION, CAPABILITY_REPRODUCIBILITY,
					"runs-on pinned to a specific version", document.jobs().get(0).location()));
		}

		List<PipelineStep> actionSteps = document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.filter(step -> step.uses() != null && step.uses().contains("@"))
				.toList();
		if (!actionSteps.isEmpty()) {
			boolean anyUnpinned = actionSteps.stream()
					.anyMatch(step -> !PINNED_ACTION_SHA.matcher(step.uses()).find()
							&& UNPINNED_ACTION_TAG.matcher(step.uses()).find());
			if (anyUnpinned) {
				PipelineStep step = actionSteps.stream()
						.filter(s -> !PINNED_ACTION_SHA.matcher(s.uses()).find()
								&& UNPINNED_ACTION_TAG.matcher(s.uses()).find())
						.findFirst()
						.orElseThrow();
				findings.add(CapabilityFinding.smell("UNPINNED_ACTION_VERSION", DIMENSION, CAPABILITY_REPRODUCIBILITY,
						step.uses(), step.location()));
			}
			else {
				findings.add(CapabilityFinding.positive("PINNED_ACTION_VERSIONS", DIMENSION, CAPABILITY_REPRODUCIBILITY,
						"all uses: references pinned to commit SHA", actionSteps.get(0).location()));
			}
		}

		document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.filter(step -> hasText(step.command()))
				.filter(step -> UNPINNED_NPM_INSTALL.matcher(step.command()).find()
						|| UNPINNED_PIP_INSTALL.matcher(step.command()).find())
				.findFirst()
				.ifPresent(step -> findings.add(CapabilityFinding.smell("UNPINNED_PACKAGE_VERSION", DIMENSION,
						CAPABILITY_REPRODUCIBILITY, step.command(), step.location())));

		return findings;
	}

	private List<CapabilityFinding> matrixCache(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		PipelineJob firstJob = document.jobs().stream().findFirst().orElse(null);
		String anyLocation = firstJob != null ? firstJob.location() : "workflow";

		boolean hasMultiOs = document.jobs().stream()
				.anyMatch(job -> hasText(job.details()) && MULTI_OS_MATRIX.matcher(job.details()).find());
		if (hasMultiOs) {
			findings.add(CapabilityFinding.positive("MULTI_OS_TEST_PRESENT", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"matrix.os spans multiple values", anyLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("NO_MULTI_OS_TEST", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"no multi-OS matrix detected", anyLocation));
		}

		boolean hasMultiVersion = document.jobs().stream()
				.anyMatch(job -> hasText(job.details()) && MULTI_VERSION_MATRIX.matcher(job.details()).find());
		if (hasMultiVersion) {
			findings.add(CapabilityFinding.positive("MULTI_VERSION_TEST_PRESENT", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"matrix language-version spans multiple values", anyLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("NO_MULTI_VERSION_TEST", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"no multi-version matrix detected", anyLocation));
		}

		boolean cachePresent = document.jobs().stream()
				.flatMap(job -> job.steps().stream())
				.anyMatch(this::isCacheStep)
				|| document.jobs().stream()
						.anyMatch(job -> hasText(job.details())
								&& job.details().toLowerCase(Locale.ROOT).contains("cache"));
		if (cachePresent) {
			findings.add(CapabilityFinding.positive("CACHE_SIGNAL_PRESENT", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"cache action or cache: key detected", anyLocation));
		}
		else {
			findings.add(CapabilityFinding.smell("MISSING_CACHE_SIGNAL", DIMENSION, CAPABILITY_MATRIX_CACHE,
					"no actions/cache or cache: signal detected", anyLocation));
		}

		return findings;
	}

	private boolean isCacheStep(PipelineStep step) {
		if (step.uses() == null) {
			return false;
		}
		String uses = step.uses().toLowerCase(Locale.ROOT);
		return CACHE_ACTION_PREFIXES.stream().anyMatch(uses::startsWith);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String fieldLocation(String baseLocation, String field) {
		if (baseLocation == null) {
			return null;
		}
		int lastDot = baseLocation.lastIndexOf('.');
		if (lastDot < 0) {
			return baseLocation + "." + field;
		}
		return baseLocation.substring(0, lastDot) + "." + field;
	}
}
