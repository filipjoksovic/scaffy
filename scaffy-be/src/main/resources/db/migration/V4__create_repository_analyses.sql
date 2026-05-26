CREATE TABLE repository_analyses (
	repository_connection_id UUID PRIMARY KEY REFERENCES repository_connections(id) ON DELETE CASCADE,
	workflow_path TEXT NOT NULL,
	provider VARCHAR(64) NOT NULL,
	overall_score DOUBLE PRECISION NOT NULL,
	overall_level INTEGER NOT NULL,
	overall_status VARCHAR(32) NOT NULL,
	analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	analysis_schema_version INTEGER NOT NULL,
	analyzer_model_version VARCHAR(128) NOT NULL,
	analysis_json TEXT NOT NULL
);

CREATE INDEX repository_analyses_analyzed_at_idx ON repository_analyses(analyzed_at);
