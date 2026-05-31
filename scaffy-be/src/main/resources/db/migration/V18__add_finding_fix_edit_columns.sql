ALTER TABLE finding_fixes ADD COLUMN edit_mode VARCHAR(32);
ALTER TABLE finding_fixes ADD COLUMN edit_after_line INTEGER;
ALTER TABLE finding_fixes ADD COLUMN edit_start_line INTEGER;
ALTER TABLE finding_fixes ADD COLUMN edit_end_line INTEGER;
ALTER TABLE finding_fixes ADD COLUMN edit_code TEXT;
