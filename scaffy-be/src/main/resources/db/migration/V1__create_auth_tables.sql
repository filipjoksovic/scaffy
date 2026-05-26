CREATE TABLE users (
	id UUID PRIMARY KEY,
	email VARCHAR(320),
	display_name VARCHAR(255),
	avatar_url TEXT,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oauth_accounts (
	id UUID PRIMARY KEY,
	user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	provider VARCHAR(32) NOT NULL,
	provider_user_id VARCHAR(255) NOT NULL,
	email VARCHAR(320),
	display_name VARCHAR(255),
	avatar_url TEXT,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT oauth_accounts_provider_user_unique UNIQUE (provider, provider_user_id)
);

CREATE INDEX oauth_accounts_user_id_idx ON oauth_accounts(user_id);
