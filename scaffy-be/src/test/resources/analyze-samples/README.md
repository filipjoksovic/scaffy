# Build analyzer sample pipelines

Use these files to manually exercise `/api/analyze` with different build maturity signals.

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

Example:

```bash
curl -s \
  -F "file=@scaffy-be/src/test/resources/analyze-samples/github-04-node-build-with-artifact.yml;type=application/x-yaml" \
  http://localhost:8080/api/analyze | jq
```
