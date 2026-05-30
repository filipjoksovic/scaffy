-- Record failed analysis attempts as runs too, so the failure (and its reason) is persisted and
-- shown alongside successful runs. Existing rows are successful.
ALTER TABLE repository_analysis_runs
	ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'succeeded';

ALTER TABLE repository_analysis_runs
	ADD COLUMN error_message TEXT;
