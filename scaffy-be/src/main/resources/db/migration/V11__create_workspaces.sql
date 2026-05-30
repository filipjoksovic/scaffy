CREATE TABLE workspaces (
	id UUID PRIMARY KEY,
	name VARCHAR(255) NOT NULL,
	slug VARCHAR(255) NOT NULL UNIQUE,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_members (
	id UUID PRIMARY KEY,
	workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
	user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	role VARCHAR(32) NOT NULL DEFAULT 'member',
	joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT workspace_members_unique UNIQUE (workspace_id, user_id)
);

CREATE INDEX workspace_members_user_id_idx ON workspace_members(user_id);

CREATE TABLE workspace_invitations (
	id UUID PRIMARY KEY,
	workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
	email VARCHAR(320) NOT NULL,
	role VARCHAR(32) NOT NULL DEFAULT 'member',
	token VARCHAR(255) NOT NULL UNIQUE,
	invited_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
	status VARCHAR(32) NOT NULL DEFAULT 'pending',
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
	accepted_at TIMESTAMP WITH TIME ZONE,
	CONSTRAINT workspace_invitations_pending_unique UNIQUE (workspace_id, email)
);

CREATE INDEX workspace_invitations_email_idx ON workspace_invitations(email);

ALTER TABLE repository_connections
	ADD COLUMN workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE;

ALTER TABLE repository_publication_jobs
	ADD COLUMN workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL;

-- Backfill: give every existing user a personal workspace. The workspace id reuses the
-- user's id so existing rows can be re-pointed with plain set-based SQL (portable to H2).
INSERT INTO workspaces (id, name, slug)
SELECT
	u.id,
	COALESCE(NULLIF(TRIM(u.display_name), ''), NULLIF(TRIM(u.email), ''), 'Workspace') || '''s workspace',
	'ws-' || REPLACE(CAST(u.id AS VARCHAR), '-', '')
FROM users u;

INSERT INTO workspace_members (id, workspace_id, user_id, role)
SELECT u.id, u.id, u.id, 'owner'
FROM users u;

UPDATE repository_connections
SET workspace_id = user_id;

UPDATE repository_publication_jobs
SET workspace_id = user_id
WHERE user_id IS NOT NULL;

ALTER TABLE repository_connections
	DROP CONSTRAINT repository_connections_user_repo_unique;

ALTER TABLE repository_connections
	ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE repository_connections
	ADD CONSTRAINT repository_connections_workspace_repo_unique UNIQUE (
		workspace_id,
		provider,
		repository_owner,
		repository_name
	);

CREATE INDEX repository_connections_workspace_id_idx ON repository_connections(workspace_id);
