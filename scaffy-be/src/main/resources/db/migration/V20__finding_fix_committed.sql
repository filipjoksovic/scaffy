ALTER TABLE finding_fixes
	ADD COLUMN committed_at TIMESTAMP WITH TIME ZONE,
	ADD COLUMN commit_sha VARCHAR(80),
	ADD COLUMN commit_url TEXT,
	ADD COLUMN commit_branch VARCHAR(255);
