# ADR-001: Runtime metrics are not integrated into the static analysis score

Status: Accepted (2026-06-02)

Context: Issue #70 added GitHub Actions workflow runtime metrics 
(success rate, durations, deploy stability, recent failures, trend). 
The question arose whether these should modify the static analyzer's 
score.

Decision: Runtime metrics are displayed alongside static analysis 
but do not affect the score. Operational telemetry is presented as 
a separate "Operational Health" section.

Rationale: Static analysis measures configuration maturity (how the 
pipeline is written). Runtime metrics measure operational 
reliability (how it actually behaves). Aggregating them produces 
misleading results — e.g., infrastructure failures unrelated to the 
YAML would lower the score of a well-written configuration.

Consequences: Score remains explainable and stable. Two views 
(configuration vs. operation) require user interpretation, but each 
is independently actionable. Future iteration MAY add combined 
scoring with empirical calibration; deferred to follow-up issue.