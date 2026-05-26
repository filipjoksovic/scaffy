ALTER TABLE oauth_accounts ADD COLUMN access_token_encrypted TEXT;
ALTER TABLE oauth_accounts ADD COLUMN access_token_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE oauth_accounts ADD COLUMN scopes TEXT;
