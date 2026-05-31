-- Fresh start: remove every connected project, its analyses, and publication history, and reset
-- all provider tokens so users begin disconnected. Identity (users/workspaces/memberships) is kept;
-- repository access now happens through an explicit connect step in workspace settings.

DELETE FROM repository_publication_job_logs;
DELETE FROM repository_publication_jobs;

-- Deleting connections cascades to repository_analyses and repository_analysis_runs.
DELETE FROM repository_connections;

DELETE FROM workspace_gitlab_instances;

UPDATE oauth_accounts
SET access_token_encrypted = NULL,
	access_token_expires_at = NULL,
	scopes = NULL;
