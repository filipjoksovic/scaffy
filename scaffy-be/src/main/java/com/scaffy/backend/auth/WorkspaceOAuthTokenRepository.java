package com.scaffy.backend.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-workspace provider access tokens. A user connects GitHub/GitLab separately in each workspace,
 * so the same person can be connected in one workspace and not another (or use different accounts).
 */
@Repository
public class WorkspaceOAuthTokenRepository {

	private final JdbcTemplate jdbcTemplate;

	public WorkspaceOAuthTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsert(
			UUID workspaceId,
			UUID userId,
			String provider,
			String instance,
			String providerUserId,
			String displayName,
			String encryptedAccessToken,
			Instant expiresAt,
			Set<String> scopes) {
		String normalizedInstance = instance == null ? "" : instance;
		OffsetDateTime expires = expiresAt == null ? null : OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
		String scopeText = scopes == null ? null : String.join(" ", scopes);
		int updated = jdbcTemplate.update("""
				UPDATE workspace_oauth_tokens
				SET provider_user_id = ?, display_name = ?, access_token_encrypted = ?,
				    access_token_expires_at = ?, scopes = ?, updated_at = CURRENT_TIMESTAMP
				WHERE workspace_id = ? AND user_id = ? AND provider = ? AND provider_instance = ?
				""",
				providerUserId, displayName, encryptedAccessToken, expires, scopeText,
				workspaceId, userId, provider, normalizedInstance);
		if (updated == 0) {
			jdbcTemplate.update("""
					INSERT INTO workspace_oauth_tokens
					    (id, workspace_id, user_id, provider, provider_instance, provider_user_id,
					     display_name, access_token_encrypted, access_token_expires_at, scopes)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					UUID.randomUUID(), workspaceId, userId, provider, normalizedInstance, providerUserId,
					displayName, encryptedAccessToken, expires, scopeText);
		}
	}

	public Optional<OAuthAccessTokenRecord> findToken(UUID workspaceId, UUID userId, String provider, String instance) {
		return jdbcTemplate.query("""
				SELECT access_token_encrypted, access_token_expires_at, scopes
				FROM workspace_oauth_tokens
				WHERE workspace_id = ? AND user_id = ? AND provider = ? AND provider_instance = ?
				    AND access_token_encrypted IS NOT NULL
				""", (rs, rowNum) -> new OAuthAccessTokenRecord(
				rs.getString("access_token_encrypted"),
				rs.getObject("access_token_expires_at", OffsetDateTime.class),
				rs.getString("scopes")),
				workspaceId, userId, provider, instance == null ? "" : instance).stream().findFirst();
	}

	public List<ProviderConnectionRecord> listConnections(UUID workspaceId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT provider, provider_instance, display_name, scopes, updated_at
				FROM workspace_oauth_tokens
				WHERE workspace_id = ? AND user_id = ? AND access_token_encrypted IS NOT NULL
				ORDER BY provider, provider_instance
				""", (rs, rowNum) -> new ProviderConnectionRecord(
				rs.getString("provider"),
				rs.getString("provider_instance"),
				rs.getString("display_name"),
				rs.getString("scopes"),
				true,
				rs.getObject("updated_at", OffsetDateTime.class)),
				workspaceId, userId);
	}

	public int delete(UUID workspaceId, UUID userId, String provider, String instance) {
		return jdbcTemplate.update("""
				DELETE FROM workspace_oauth_tokens
				WHERE workspace_id = ? AND user_id = ? AND provider = ? AND provider_instance = ?
				""", workspaceId, userId, provider, instance == null ? "" : instance);
	}
}
