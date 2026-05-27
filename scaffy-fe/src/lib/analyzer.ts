import type {
  CapabilityFinding,
  CapabilityScore,
  DimensionAnalysis,
} from "../api/analyze";

const ACCEPTED_EXTENSIONS = [".yml", ".yaml"];

type AnalyzerMeta = {
  label: string;
  description: string;
};

const STATUS_META: Record<string, AnalyzerMeta> = {
  complete: {
    label: "Complete",
    description:
      "The expected practice is fully represented by detected pipeline evidence.",
  },
  missing: {
    label: "Missing",
    description:
      "The expected practice was not detected in the analyzed pipeline.",
  },
  partial: {
    label: "Partial",
    description:
      "The pipeline has some evidence, but still has gaps or smells.",
  },
  not_evaluated: {
    label: "Not evaluated",
    description:
      "No relevant signals were found for this dimension, so it is excluded from the score.",
  },
};

const FINDING_TYPE_META: Record<string, AnalyzerMeta> = {
  POSITIVE: {
    label: "Detected",
    description: "Evidence that the pipeline implements this practice.",
  },
  SMELL: {
    label: "Needs review",
    description: "A risky or weak implementation pattern was detected.",
  },
  MISSING: {
    label: "Missing",
    description: "Expected evidence for this capability was not found.",
  },
};

const DIMENSION_META: Record<string, AnalyzerMeta> = {
  build_release: {
    label: "Build & release management",
    description:
      "Build commands, dependency handling, artifacts, registry publishing, and versioning.",
  },
  testing_maturity: {
    label: "Testing maturity",
    description:
      "Automated test execution, CI integration, reports, coverage, and test-layer breadth.",
  },
  security_integration: {
    label: "Security integration",
    description:
      "SAST, dependency and container scanning, secret hygiene, permissions, and policy checks.",
  },
  deployment_automation: {
    label: "Deployment automation",
    description:
      "Deployment commands, environments, IaC, orchestration, and rollout validation.",
  },
  workflow_quality: {
    label: "Workflow quality & optimization",
    description:
      "Execution safety, maintainability, reproducibility, selective execution, cache, and notifications.",
  },
};

const CAPABILITY_META: Record<string, AnalyzerMeta> = {
  "Build scripting maturity": {
    label: "Build scripting maturity",
    description:
      "Build jobs should run automatically and use recognizable build commands or build actions.",
  },
  "Dependency handling": {
    label: "Dependency handling",
    description:
      "Dependencies should be installed before build steps, preferably with deterministic install commands.",
  },
  "Packaging & artifacts": {
    label: "Packaging & artifacts",
    description:
      "The pipeline should create reusable artifacts, archives, packages, or images.",
  },
  "Registry / release publish": {
    label: "Registry / release publish",
    description:
      "Release outputs should be published to a registry or reused across pipeline stages.",
  },
  "Versioning / tagging": {
    label: "Versioning / tagging",
    description:
      "Artifacts should carry a commit, tag, version, or image identity that can be traced.",
  },
  "Test presence": {
    label: "Test presence",
    description:
      "The pipeline should execute a recognizable test command or test action.",
  },
  "CI-integrated tests": {
    label: "CI-integrated tests",
    description:
      "Tests should run as part of CI rather than being manual-only.",
  },
  "Reports & coverage": {
    label: "Reports & coverage",
    description:
      "Test jobs should publish reports, coverage, or other machine-readable test output.",
  },
  "Multi-layer testing": {
    label: "Multi-layer testing",
    description:
      "The pipeline shows more than one testing layer, such as unit, integration, or end-to-end tests.",
  },
  "Execution safety": {
    label: "Execution safety",
    description:
      "Jobs should use timeouts, concurrency control, and avoid hiding failures.",
  },
  "Selective execution": {
    label: "Selective execution",
    description:
      "Workflows should avoid unnecessary or unsafe runs through path filters and fork guards.",
  },
  Maintainability: {
    label: "Maintainability",
    description:
      "Jobs and steps should be named clearly and avoid hard-to-maintain shell blocks.",
  },
  Reproducibility: {
    label: "Reproducibility",
    description:
      "Runners, actions, and package installs should avoid mutable latest or floating versions.",
  },
  "Matrix / cache optimization": {
    label: "Matrix / cache optimization",
    description:
      "Workflows should test relevant OS/version combinations and cache dependencies when useful.",
  },
  "Lint / static analysis": {
    label: "Lint / static analysis",
    description: "The pipeline should run linting or static analysis commands.",
  },
  Formatting: {
    label: "Formatting",
    description: "The pipeline should verify formatting or style consistency.",
  },
  "Type checking": {
    label: "Type checking",
    description:
      "The pipeline should run type checks or deeper language-aware analysis.",
  },
  "Static analysis": {
    label: "Static security analysis",
    description:
      "The pipeline should run SAST or equivalent static security scanning.",
  },
  "Dependency / container scanning": {
    label: "Dependency / container scanning",
    description:
      "Dependencies, container images, or IaC files should be scanned for known issues.",
  },
  "Secret hygiene": {
    label: "Secret hygiene",
    description:
      "The pipeline should scan for secrets and avoid hardcoded sensitive values.",
  },
  "Safe action/token usage": {
    label: "Safe action/token usage",
    description:
      "GitHub Actions should declare token permissions and avoid over-permissive or mutable actions.",
  },
  "Policy as code": {
    label: "Policy as code",
    description:
      "The pipeline should include policy or IaC guardrails such as Checkov, OPA, or related tools.",
  },
  "Deployment stage presence": {
    label: "Deployment stage presence",
    description: "A deployment job or deployment command should be present.",
  },
  "Environment targeting": {
    label: "Environment targeting",
    description:
      "Deployment jobs should declare or clearly infer a target environment.",
  },
  "IaC usage": {
    label: "Infrastructure as code usage",
    description:
      "Deployments should use IaC tooling or deploy a traceable built artifact/image.",
  },
  "Orchestration maturity": {
    label: "Orchestration maturity",
    description:
      "The pipeline should separate build, test, and deploy concerns across stages or jobs.",
  },
  "Rollback / controlled rollout": {
    label: "Rollback / controlled rollout",
    description:
      "Deployments should include rollout validation, rollback, smoke test, or health-check signals.",
  },
  "Notification channel": {
    label: "Notification channel",
    description:
      "The pipeline should notify a real delivery channel such as Slack, Teams, email, or webhook.",
  },
  "Status-based alerting": {
    label: "Status-based alerting",
    description:
      "Notifications should be conditioned on failure, cancellation, success, or overall status.",
  },
};

const RULE_META: Record<string, AnalyzerMeta> = {
  ARTIFACT_IMAGE_USED: {
    label: "Deployment uses built artifact or image",
    description:
      "Deployment references a build artifact, image, or commit-derived image tag.",
  },
  ARTIFACT_OUTPUT_PRESENT: {
    label: "Artifact output is produced",
    description:
      "The pipeline creates or uploads a reusable package, archive, image, or artifact.",
  },
  ARTIFACT_REUSE_PRESENT: {
    label: "Artifact reuse detected",
    description:
      "A downstream stage reuses a previously built artifact or image.",
  },
  BUILD_AUTOMATIC_TRIGGER: {
    label: "Build runs automatically",
    description:
      "The build is connected to an automatic CI trigger or non-manual job.",
  },
  BUILD_ONLY_PIPELINE: {
    label: "Build-only pipeline",
    description:
      "The pipeline builds automatically but does not publish artifacts or releases.",
  },
  BUILD_STAGE_PRESENT: {
    label: "Build stage present",
    description:
      "A recognizable build command or Docker build action was detected.",
  },
  CACHE_SIGNAL_PRESENT: {
    label: "Cache signal present",
    description:
      "Dependency or tool caching is configured through an action or cache key.",
  },
  CHECKOV_OR_OPA_MISSING: {
    label: "Policy-as-code check missing",
    description:
      "No Checkov, OPA, Conftest, tfsec, KICS, or similar policy tool was detected.",
  },
  CI_INTEGRATED_TESTS: {
    label: "Tests are CI-integrated",
    description:
      "Tests run through CI automation instead of only through a manual job.",
  },
  CONCURRENCY_CONTROL_PRESENT: {
    label: "Concurrency control present",
    description:
      "The workflow limits overlapping runs with a concurrency configuration.",
  },
  CONTAINER_SCAN_PRESENT: {
    label: "Container or IaC scan present",
    description:
      "A container image, filesystem, or infrastructure configuration scan was detected.",
  },
  CONTINUE_ON_ERROR_USED: {
    label: "Continue-on-error used",
    description:
      "A step can fail without failing the job, which can hide delivery problems.",
  },
  COVERAGE_TOOL_PRESENT: {
    label: "Coverage tooling present",
    description: "The test job emits or uploads coverage information.",
  },
  DEFAULT_JOB_NAME: {
    label: "Default job name",
    description:
      "A job uses a generic id such as build, test, deploy, or job1.",
  },
  DEPENDENCY_INSTALL_PRESENT: {
    label: "Dependency install present",
    description: "Dependencies are installed before the build step.",
  },
  DEPENDENCY_SCAN_MISSING: {
    label: "Dependency or container scan missing",
    description:
      "No dependency, SCA, container, or IaC vulnerability scan was detected.",
  },
  DEPENDENCY_SCAN_PRESENT: {
    label: "Dependency scan present",
    description:
      "A dependency or software composition analysis command/action was detected.",
  },
  DEPLOYMENT_STAGE_PRESENT: {
    label: "Deployment stage present",
    description:
      "A deployment command, script, remote copy, or platform deploy action was detected.",
  },
  DETERMINISTIC_INSTALL_PRESENT: {
    label: "Deterministic dependency install",
    description:
      "Dependencies are installed using a deterministic command such as npm ci.",
  },
  ENVIRONMENT_DECLARED: {
    label: "Deployment environment declared",
    description:
      "The deploy job declares or strongly implies a target environment.",
  },
  FORMATTER_MISSING: {
    label: "Formatting check missing",
    description: "No formatting or style check command was detected.",
  },
  FORMATTER_PRESENT: {
    label: "Formatting check present",
    description: "A formatter or style verification command was detected.",
  },
  GITHUB_TOKEN_OVERPERMISSIVE: {
    label: "GitHub token is over-permissive",
    description: "The workflow grants broad write-all token permissions.",
  },
  HARDCODED_SECRET_IN_ENV: {
    label: "Potential hardcoded secret",
    description:
      "A secret-like value appears directly in pipeline configuration.",
  },
  IAC_PRESENT: {
    label: "Infrastructure-as-code tool present",
    description:
      "Deployment uses an IaC tool such as Terraform, Ansible, Pulumi, or Helm.",
  },
  IaC_NOT_PRESENT: {
    label: "Infrastructure-as-code signal missing",
    description:
      "No IaC tool or traceable artifact/image deployment signal was detected.",
  },
  LINT_STATIC_MISSING: {
    label: "Lint or static analysis missing",
    description:
      "No linting or general static analysis command/action was detected.",
  },
  LINT_STATIC_PRESENT: {
    label: "Lint or static analysis present",
    description: "A linting or static analysis command/action was detected.",
  },
  MANUAL_ONLY_TEST_JOB: {
    label: "Manual-only test job",
    description: "All detected tests appear to require manual execution.",
  },
  MISSING_CACHE_SIGNAL: {
    label: "Cache signal missing",
    description: "No dependency or tool caching configuration was detected.",
  },
  MISSING_CONCURRENCY_CONTROL: {
    label: "Concurrency control missing",
    description: "The workflow does not appear to limit overlapping runs.",
  },
  MISSING_ENVIRONMENT_DECLARATION: {
    label: "Deployment environment missing",
    description: "No explicit or inferred deployment environment was detected.",
  },
  MISSING_PACKAGE_MANAGEMENT: {
    label: "Package management step missing",
    description:
      "No dependency installation step was found before build execution.",
  },
  MISSING_PERMISSIONS: {
    label: "GitHub permissions missing",
    description: "No explicit GitHub Actions permissions block was detected.",
  },
  MISSING_TIMEOUT: {
    label: "Job timeout missing",
    description: "At least one job has no timeout-minutes configuration.",
  },
  MONOLITHIC_BUILD_PIPELINE: {
    label: "Monolithic deployment pipeline",
    description:
      "Deployment appears to run in a single-stage pipeline instead of a staged flow.",
  },
  MULTI_COMMAND_STEP: {
    label: "Multi-command shell step",
    description:
      "A single run step contains multiple shell commands, reducing maintainability.",
  },
  MULTI_LAYER_TEST_SIGNAL: {
    label: "Multiple test layers detected",
    description:
      "The pipeline references more than one test layer, such as unit, integration, or e2e.",
  },
  MULTI_OS_TEST_PRESENT: {
    label: "Multi-OS matrix present",
    description: "The workflow tests across multiple operating systems.",
  },
  MULTI_STAGE_PIPELINE_PRESENT: {
    label: "Multi-stage pipeline present",
    description: "The pipeline separates work across multiple stages or jobs.",
  },
  MULTI_VERSION_TEST_PRESENT: {
    label: "Multi-version matrix present",
    description:
      "The workflow tests across multiple language or runtime versions.",
  },
  NAMED_RUN_STEPS_PRESENT: {
    label: "Run steps are named",
    description: "All detected shell run steps have explicit names.",
  },
  NON_DETERMINISTIC_INSTALL: {
    label: "Non-deterministic dependency install",
    description:
      "Dependency installation may mutate lockfiles or resolve floating dependency versions.",
  },
  NOTIFICATION_CHANNEL_PRESENT: {
    label: "Notification channel present",
    description:
      "A Slack, Teams, email, Discord, webhook, or similar notification target was detected.",
  },
  NOTIFICATION_MISSING: {
    label: "Notification channel missing",
    description:
      "No notification channel or external delivery target was detected.",
  },
  NO_COVERAGE_TOOL: {
    label: "Coverage tool missing",
    description: "No coverage signal was found in the test job.",
  },
  NO_DEPLOYMENT_STAGE: {
    label: "Deployment stage missing",
    description:
      "No deployment command, script, or platform deploy step was detected.",
  },
  NO_MULTI_OS_TEST: {
    label: "Multi-OS matrix missing",
    description:
      "No matrix test across multiple operating systems was detected.",
  },
  NO_MULTI_VERSION_TEST: {
    label: "Multi-version matrix missing",
    description:
      "No matrix test across multiple runtime versions was detected.",
  },
  NO_PATH_FILTERS: {
    label: "Path filters missing",
    description: "No push or pull request path filters were detected.",
  },
  NO_RELEASE_STAGE: {
    label: "Release publish missing",
    description:
      "No package, image, or release registry publish step was detected.",
  },
  NO_ROLLBACK_ON_FAILURE: {
    label: "Rollback or validation signal missing",
    description:
      "No rollout status, health check, smoke test, or rollback signal was detected.",
  },
  NO_TEST_REPORT_OUTPUT: {
    label: "Test report output missing",
    description:
      "No test report, coverage output, or uploaded test artifact was detected.",
  },
  PATH_FILTERS_PRESENT: {
    label: "Path filters present",
    description: "The workflow uses path filters to avoid unnecessary runs.",
  },
  PERMISSIONS_DECLARED: {
    label: "GitHub permissions declared",
    description: "At least one job declares GitHub Actions token permissions.",
  },
  PINNED_ACTION_VERSIONS: {
    label: "Actions pinned to commit SHAs",
    description: "Action references are pinned to immutable commit SHAs.",
  },
  PINNED_RUNNER_PRESENT: {
    label: "Runner version pinned",
    description: "The workflow avoids mutable -latest runner labels.",
  },
  PIPELINE_MISSING_ARTIFACT_PUBLISH: {
    label: "Artifact output missing",
    description:
      "No reusable artifact, archive, package, or image output was detected.",
  },
  PIPELINE_MISSING_TEST_STAGE: {
    label: "Test stage missing",
    description:
      "No recognizable automated test command or test action was detected.",
  },
  POLICY_TOOL_PRESENT: {
    label: "Policy-as-code tool present",
    description:
      "A policy or IaC validation tool such as Checkov, OPA, or Conftest was detected.",
  },
  REGISTRY_PUBLISH_PRESENT: {
    label: "Registry publish present",
    description: "The pipeline publishes a package or image to a registry.",
  },
  RELEASE_TAGGING_PRESENT: {
    label: "Release versioning missing",
    description:
      "No commit, tag, version, or image identity signal was detected for release outputs.",
  },
  ROLLBACK_SIGNAL_PRESENT: {
    label: "Rollout validation present",
    description:
      "A health check, smoke test, rollout status, or rollback-capable command was detected.",
  },
  RUNS_ON_LATEST: {
    label: "Mutable latest runner",
    description:
      "The workflow uses a -latest runner label, which can change over time.",
  },
  SAST_MISSING: {
    label: "Static security scan missing",
    description: "No SAST or static security scanning signal was detected.",
  },
  SAST_PRESENT: {
    label: "Static security scan present",
    description:
      "A SAST, Semgrep, Sonar, Bandit, or similar static security scan was detected.",
  },
  SCHEDULED_RUN_ON_FORKS: {
    label: "Scheduled run lacks fork guard",
    description:
      "A scheduled workflow may run on forks without a repository-owner guard.",
  },
  SECRET_SCAN_MISSING: {
    label: "Secret scan missing",
    description:
      "No Gitleaks, TruffleHog, detect-secrets, or similar secret scan was detected.",
  },
  SECRET_SCAN_PRESENT: {
    label: "Secret scan present",
    description: "A secret scanning tool or action was detected.",
  },
  STATUS_CONDITION_MISSING: {
    label: "Status-based alert condition missing",
    description:
      "No failure, cancellation, success, or always condition was detected for notifications.",
  },
  STATUS_CONDITION_PRESENT: {
    label: "Status-based alert condition present",
    description: "Notifications are conditioned on pipeline status.",
  },
  TESTS_NOT_AUTOMATED: {
    label: "Tests are not automated",
    description: "Tests were not connected to automatic CI execution.",
  },
  TESTS_PRESENT: {
    label: "Tests present",
    description:
      "A recognizable automated test command or test action was detected.",
  },
  TEST_REPORT_OUTPUT_PRESENT: {
    label: "Test report output present",
    description:
      "The test job publishes reports, coverage, or uploaded test artifacts.",
  },
  TIMEOUT_PRESENT: {
    label: "Job timeouts present",
    description: "All jobs appear to define timeout-minutes.",
  },
  TYPE_CHECK_MISSING: {
    label: "Type check missing",
    description:
      "No type checking or deeper language-aware static analysis signal was detected.",
  },
  TYPE_CHECK_PRESENT: {
    label: "Type check present",
    description:
      "A type check or deeper language-aware static analysis command/action was detected.",
  },
  UNNAMED_RUN_STEP: {
    label: "Unnamed run step",
    description: "A shell run step does not have a human-readable name.",
  },
  UNPINNED_ACTION_VERSION: {
    label: "Unpinned action version",
    description:
      "An action uses a mutable tag instead of an immutable commit SHA.",
  },
  UNPINNED_PACKAGE_VERSION: {
    label: "Unpinned package version",
    description:
      "A package install command appears to use an unpinned dependency version.",
  },
  UPLOAD_ARTIFACT_ON_FORKS: {
    label: "Artifact upload lacks fork guard",
    description:
      "An artifact upload may run on forks without a repository-owner guard.",
  },
  VERSIONED_ARTIFACT: {
    label: "Artifact versioning present",
    description:
      "A tag, commit SHA, version variable, or image tag provides traceable artifact identity.",
  },
};

export function collectFindings(
  dimension: DimensionAnalysis,
  type: CapabilityFinding["type"],
): CapabilityFinding[] {
  return dimension.capabilityScores.flatMap((capability: CapabilityScore) =>
    capability.findings.filter((finding) => finding.type === type),
  );
}

export function countIssues(dimension: DimensionAnalysis): number {
  return (
    collectFindings(dimension, "SMELL").length +
    collectFindings(dimension, "MISSING").length
  );
}

export function findingKey(finding: CapabilityFinding): string {
  return `${finding.ruleId}-${finding.location ?? ""}-${finding.evidence ?? ""}`;
}

export function validateFile(file: File | null): string | null {
  if (!file) return null;
  const normalizedName = file.name.toLowerCase();
  if (
    !ACCEPTED_EXTENSIONS.some((extension) => normalizedName.endsWith(extension))
  ) {
    return "Upload a .yml or .yaml pipeline file.";
  }
  return null;
}

export function formatScore(score: number): string {
  return `${Math.round(score * 100)}%`;
}

const MATURITY_LEVEL_LABELS: Record<number, string> = {
  1: "Initial / Chaos",
  2: "Basic CI",
  3: "Structured Delivery",
  4: "Governed Automation",
  5: "Advanced Pipeline",
};

export function maturityLevelLabel(level: number): string {
  return MATURITY_LEVEL_LABELS[level] ?? `Level ${level}`;
}

export function formatMaturityLevel(level: number): string {
  const label = MATURITY_LEVEL_LABELS[level];
  return label ? `L${level} · ${label}` : `Level ${level}`;
}

export function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function formatProvider(provider: string): string {
  if (provider === "github-actions") return "GitHub Actions";
  if (provider === "gitlab-ci") return "GitLab CI";
  return formatLabel(provider);
}

export function formatDimension(dimension: string): string {
  return dimensionMeta(dimension).label;
}

export function formatLabel(value: string): string {
  return value
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

export function statusMeta(status: string): AnalyzerMeta {
  return (
    STATUS_META[status] ?? {
      label: formatLabel(status),
      description: "Analyzer status returned by the backend.",
    }
  );
}

export function findingTypeMeta(type: CapabilityFinding["type"]): AnalyzerMeta {
  return (
    FINDING_TYPE_META[type] ?? {
      label: formatLabel(type),
      description: "Analyzer finding type returned by the backend.",
    }
  );
}

export function dimensionMeta(dimension: string): AnalyzerMeta {
  return (
    DIMENSION_META[dimension] ?? {
      label: formatLabel(dimension),
      description: "Analyzer dimension returned by the backend.",
    }
  );
}

export function capabilityMeta(capability: string): AnalyzerMeta {
  return (
    CAPABILITY_META[capability] ?? {
      label: formatLabel(capability),
      description: "Capability returned by the backend analyzer.",
    }
  );
}

export function ruleMeta(ruleId: string): AnalyzerMeta {
  return (
    RULE_META[ruleId] ?? {
      label: formatLabel(ruleId),
      description: "Rule returned by the backend analyzer.",
    }
  );
}

export function dimensionSummary(dimension: DimensionAnalysis): string {
  if (dimension.status === "not_evaluated") {
    return "Not evaluated";
  }
  const issues = countIssues(dimension);
  return `${issues} issue${issues === 1 ? "" : "s"}`;
}

export function statusBadgeClassName(status: string): string | undefined {
  return status === "not_evaluated" ? "badge badge--not-evaluated" : undefined;
}
