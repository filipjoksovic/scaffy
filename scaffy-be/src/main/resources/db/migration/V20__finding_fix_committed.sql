ALTER TABLE finding_fixes ADD COLUMN committed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE finding_fixes ADD COLUMN commit_sha VARCHAR(80);
ALTER TABLE finding_fixes ADD COLUMN commit_url TEXT;
ALTER TABLE finding_fixes ADD COLUMN commit_branch VARCHAR(255);
