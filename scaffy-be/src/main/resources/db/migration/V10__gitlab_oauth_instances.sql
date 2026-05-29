CREATE TABLE oauth_instances (
	id UUID PRIMARY KEY,
	registration_id VARCHAR(64) NOT NULL,
	provider VARCHAR(32) NOT NULL DEFAULT 'gitlab',
	base_url TEXT NOT NULL,
	host VARCHAR(255) NOT NULL,
	display_name VARCHAR(255),
	client_id TEXT NOT NULL,
	client_secret_encrypted TEXT NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT oauth_instances_registration_id_unique UNIQUE (registration_id),
	CONSTRAINT oauth_instances_host_unique UNIQUE (host)
);

ALTER TABLE oauth_accounts ADD COLUMN provider_instance VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE oauth_accounts DROP CONSTRAINT oauth_accounts_provider_user_unique;

ALTER TABLE oauth_accounts
	ADD CONSTRAINT oauth_accounts_provider_user_unique UNIQUE (provider, provider_instance, provider_user_id);
