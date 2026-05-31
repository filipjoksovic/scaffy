-- Provider connections (GitHub / GitLab) become per-workspace: each member connects their own
-- account separately in each workspace. Identity stays in oauth_accounts; repository access tokens
-- move here, keyed by (workspace, user, provider, instance).
CREATE TABLE workspace_oauth_tokens (
	id UUID PRIMARY KEY,
	workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
	user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	provider VARCHAR(32) NOT NULL,
	provider_instance VARCHAR(255) NOT NULL DEFAULT '',
	provider_user_id VARCHAR(255),
	display_name VARCHAR(255),
	access_token_encrypted TEXT NOT NULL,
	access_token_expires_at TIMESTAMP WITH TIME ZONE,
	scopes TEXT,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT workspace_oauth_tokens_unique UNIQUE (workspace_id, user_id, provider, provider_instance)
);

CREATE INDEX workspace_oauth_tokens_lookup_idx
	ON workspace_oauth_tokens(workspace_id, user_id, provider, provider_instance);

-- Repository access tokens previously lived (per-user) on oauth_accounts. They no longer grant
-- repository access, so clear them; members reconnect per workspace.
UPDATE oauth_accounts
SET access_token_encrypted = NULL,
	access_token_expires_at = NULL,
	scopes = NULL;
