CREATE TABLE repository_connections (
	id UUID PRIMARY KEY,
	user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	provider VARCHAR(32) NOT NULL,
	repository_owner VARCHAR(255) NOT NULL,
	repository_name VARCHAR(255) NOT NULL,
	repository_url TEXT NOT NULL,
	connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT repository_connections_user_repo_unique UNIQUE (
		user_id,
		provider,
		repository_owner,
		repository_name
	)
);

CREATE INDEX repository_connections_user_id_idx ON repository_connections(user_id);
