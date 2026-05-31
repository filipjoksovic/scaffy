-- Generalize repository connections beyond GitHub: a provider_instance disambiguates
-- self-hosted GitLab hosts (empty string for github / gitlab.com style single hosts).
ALTER TABLE repository_connections
	ADD COLUMN provider_instance VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE repository_connections
	DROP CONSTRAINT repository_connections_workspace_repo_unique;

ALTER TABLE repository_connections
	ADD CONSTRAINT repository_connections_workspace_repo_unique UNIQUE (
		workspace_id,
		provider,
		provider_instance,
		repository_owner,
		repository_name
	);

-- Workspace-level self-hosted GitLab instances. The OAuth client (client id/secret) lives in
-- the global oauth_instances table (so the dynamic ClientRegistrationRepository can resolve it);
-- this table records which workspace registered the instance. Members connect their own account
-- against the instance via OAuth (per-user tokens) before adding projects.
CREATE TABLE workspace_gitlab_instances (
	id UUID PRIMARY KEY,
	workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
	registration_id VARCHAR(64) NOT NULL,
	host VARCHAR(255) NOT NULL,
	base_url TEXT NOT NULL,
	display_name VARCHAR(255),
	created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT workspace_gitlab_instances_unique UNIQUE (workspace_id, host)
);

CREATE INDEX workspace_gitlab_instances_workspace_id_idx
	ON workspace_gitlab_instances(workspace_id);
