CREATE TABLE repository_analysis_runs (
	id UUID PRIMARY KEY,
	repository_connection_id UUID NOT NULL REFERENCES repository_connections(id) ON DELETE CASCADE,
	run_number INTEGER NOT NULL,
	workflow_path TEXT NOT NULL,
	workflow_content_hash VARCHAR(64),
	provider VARCHAR(64) NOT NULL,
	overall_score DOUBLE PRECISION NOT NULL,
	overall_level INTEGER NOT NULL,
	overall_status VARCHAR(32) NOT NULL,
	analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	analysis_schema_version INTEGER NOT NULL,
	analyzer_model_version VARCHAR(128) NOT NULL,
	analysis_json TEXT NOT NULL,
	CONSTRAINT repository_analysis_runs_connection_run_unique UNIQUE (repository_connection_id, run_number)
);

INSERT INTO repository_analysis_runs (
	id,
	repository_connection_id,
	run_number,
	workflow_path,
	workflow_content_hash,
	provider,
	overall_score,
	overall_level,
	overall_status,
	analyzed_at,
	analysis_schema_version,
	analyzer_model_version,
	analysis_json
)
SELECT
	repository_connection_id,
	repository_connection_id,
	1,
	workflow_path,
	NULL,
	provider,
	overall_score,
	overall_level,
	overall_status,
	analyzed_at,
	analysis_schema_version,
	analyzer_model_version,
	analysis_json
FROM repository_analyses;

CREATE INDEX repository_analysis_runs_connection_analyzed_at_idx
	ON repository_analysis_runs(repository_connection_id, analyzed_at DESC);

DROP TABLE repository_analyses;
