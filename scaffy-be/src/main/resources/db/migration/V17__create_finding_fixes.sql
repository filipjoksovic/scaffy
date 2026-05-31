CREATE TABLE finding_fixes (
	id UUID PRIMARY KEY,
	analysis_run_id UUID NOT NULL REFERENCES repository_analysis_runs(id) ON DELETE CASCADE,
	finding_hash VARCHAR(64) NOT NULL,
	rule_id TEXT NOT NULL,
	dimension TEXT NOT NULL,
	capability TEXT NOT NULL,
	finding_type VARCHAR(32) NOT NULL,
	status VARCHAR(32) NOT NULL,
	model VARCHAR(128),
	summary TEXT,
	explanation TEXT,
	language VARCHAR(32),
	suggested_code TEXT,
	generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT finding_fixes_run_finding_unique UNIQUE (analysis_run_id, finding_hash)
);
