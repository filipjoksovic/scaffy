# Analyzer sample pipelines

Use these files to manually exercise `/api/analyze` with different maturity signals.

Expected behavior:

| File | Expected shape |
| --- | --- |
| `github-01-test-only-missing.yml` | No build command, `score: 0.0`, `status: missing`. |
| `github-02-manual-build-low.yml` | Build command only, manual trigger, missing dependency install and output. |
| `github-03-node-build-no-artifact.yml` | Automated Node build with deterministic install, but missing artifact/output evidence. |
| `github-04-node-build-with-artifact.yml` | Complete GitHub Actions Node build with uploaded artifact. |
| `gitlab-05-docker-build-no-explicit-deps.yml` | Docker build output detected, but dependency install is not visible in YAML. |
| `gitlab-06-manual-java-build.yml` | Java build with dependency preparation, but manual-only and missing artifact/output evidence. |
| `gitlab-07-java-build-complete.yml` | Complete GitLab Maven build with dependency restore, automatic trigger, and artifacts. |
| `gitlab-08-dotnet-build-complete.yml` | Complete GitLab .NET build with restore, automatic trigger, publish, and artifacts. |
| `test-01-build-only-test-missing.yml` | Build-only workflow where the test dimension is missing. |
| `test-02-manual-test-weak.yml` | Manual-only test command without automatic CI trigger, reports, or quality signal. |
| `test-03-automated-test-partial.yml` | Automated GitLab test command without reports or coverage. |
| `test-04-test-with-artifact-strong.yml` | Automated GitHub test command with uploaded test artifact, but no explicit coverage/report quality signal. |
| `test-05-complete-test-suite.yml` | Complete GitLab test suite with report, coverage signal, and multiple test layers. |
| `deploy-01-build-test-only-missing.yml` | Build/test workflow where deployment is missing. |
| `deploy-02-manual-deploy-partial.yml` | Manual-only Kubernetes deployment with environment and image evidence. |
| `deploy-03-auto-deploy-no-validation.yml` | Automated GitLab Helm deployment without post-deploy validation. |
| `deploy-04-complete-kubernetes.yml` | Complete GitHub Kubernetes deployment with environment, image, automatic trigger, and rollout validation. |
| `deploy-05-cloud-provider.yml` | Complete GitLab GCP Cloud Run deployment with image and health check. |
| `quality-01-build-only-missing.yml` | Build-only workflow where the code analysis dimension is missing. |
| `quality-02-github-lint-partial.yml` | GitHub lint-only workflow with automatic trigger, but no formatter, type check, or report. |
| `quality-03-github-typescript-complete.yml` | Complete GitHub TypeScript code analysis with lint, typecheck, formatter, report, and automatic trigger. |
| `quality-04-gitlab-java-python.yml` | Complete GitLab code quality job with static analysis, formatter, type checking, and codequality report. |
| `quality-05-sonar-super-linter.yml` | GitHub action-based Sonar/Super-Linter workflow. |
| `artifact-01-missing.yml` | Test workflow where the artifact dimension is missing. |
| `artifact-02-github-upload-partial.yml` | GitHub build with uploaded artifact but no publish, reuse, or version signal. |
| `artifact-03-gitlab-paths-partial.yml` | GitLab build with `artifacts.paths` but no publish, reuse, or version signal. |
| `artifact-04-docker-image-complete.yml` | Complete Docker image artifact with build, push, pull/reuse, SHA tag, and automatic trigger. |
| `artifact-05-package-publish-complete.yml` | Complete package artifact with package build, publish, version signal, and automatic trigger. |
| `notification-01-missing.yml` | Test workflow where the notification dimension is missing. |
| `notification-02-github-slack-failure-complete.yml` | Complete GitHub Slack failure notification with webhook secret and automatic trigger. |
| `notification-03-gitlab-teams-on-failure.yml` | Complete GitLab Teams notification using `when: on_failure`. |
| `notification-04-discord-webhook-partial.yml` | Manual Discord webhook notification without automatic trigger or status condition. |
| `notification-05-email-notification.yml` | Email notification command with automatic trigger and pipeline status context. |
| `security-01-missing.yml` | Test workflow where the security scanning dimension is missing. |
| `security-02-github-codeql-complete.yml` | Complete GitHub security workflow with SAST, dependency, secret, image scan, SARIF report, and automatic trigger. |
| `security-03-dependency-scan-partial.yml` | Partial dependency scanning workflow with automatic pull request trigger. |
| `security-04-gitlab-security-reports-complete.yml` | Complete GitLab security report workflow with SAST, dependency, container, and secret reports. |
| `security-05-container-iac-secret-scan.yml` | Partial workflow with secret, container, and IaC scanning. |
| `workflow-01-missing-permissions.yml` | GitHub workflow without an explicit `permissions:` block — exercises the MISSING_PERMISSIONS smell. |
| `workflow-02-unpinned-actions.yml` | Actions referenced by floating `@v4` tags rather than commit SHAs — exercises UNPINNED_ACTION_VERSION. |
| `workflow-03-timeout-missing.yml` | Job without `timeout-minutes:` — exercises MISSING_TIMEOUT. |
| `workflow-04-concurrency-present.yml` | Positive: workflow with `concurrency.cancel-in-progress` — satisfies MISSING_CONCURRENCY_CONTROL. |
| `workflow-05-path-filters.yml` | Positive: trigger gated by `on.push.paths` and `on.pull_request.paths` — satisfies NO_PATH_FILTERS. |
| `workflow-06-hardcoded-secret.yml` | Anti-pattern: credentials inlined under `env:` and used in shell — exercises HARDCODED_SECRET. |
| `workflow-07-policy-as-code.yml` | Positive: policy-as-code via Conftest, Checkov, and OPA — satisfies the policy-as-code special case. |
| `workflow-08-rollback-signal.yml` | Positive GitLab CI: explicit rollback stage with `when: on_failure` and `kubectl rollout undo` — satisfies ROLLBACK_SIGNAL_PRESENT. |
| `workflow-09-default-job-names.yml` | Jobs called `job1` / `build` without descriptive names — exercises DEFAULT_JOB_NAME. |
| `workflow-10-matrix-cache-use.yml` | Positive: matrix strategy across OSes/Node versions with `actions/cache` and `setup-node` cache — satisfies NO_MULTI_OS_TEST / MISSING_CACHE_SIGNAL. |

Example:

```bash
curl -s \
  -F "file=@scaffy-be/src/test/resources/analyze-samples/github-04-node-build-with-artifact.yml;type=application/x-yaml" \
  http://localhost:8080/api/analyze | jq
```
