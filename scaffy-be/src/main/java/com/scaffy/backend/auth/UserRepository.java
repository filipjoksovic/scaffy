package com.scaffy.backend.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public AppUser upsertOAuthUser(OAuthProfile profile) {
		Optional<AppUser> existing = findByOAuthAccount(profile.provider(), profile.providerUserId());
		if (existing.isPresent()) {
			AppUser user = merge(existing.get(), profile);
			updateUser(user);
			updateOAuthAccount(user.id(), profile);
			return user;
		}

		AppUser user = new AppUser(UUID.randomUUID(), profile.email(), profile.displayName(), profile.avatarUrl());
		insertUser(user);
		insertOAuthAccount(UUID.randomUUID(), user.id(), profile);
		return user;
	}

	public Optional<AppUser> findById(UUID id) {
		return jdbcTemplate.query("""
				SELECT id, email, display_name, avatar_url
				FROM users
				WHERE id = ?
				""", this::mapUser, id).stream().findFirst();
	}

	public int updateOAuthAccessToken(
			UUID userId,
			String provider,
			String providerUserId,
			String encryptedAccessToken,
			Instant expiresAt,
			Set<String> scopes) {
		return jdbcTemplate.update("""
				UPDATE oauth_accounts
				SET access_token_encrypted = ?,
					access_token_expires_at = ?,
					scopes = ?,
					updated_at = CURRENT_TIMESTAMP
				WHERE user_id = ? AND provider = ? AND provider_user_id = ?
				""",
				encryptedAccessToken,
				expiresAt == null ? null : OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
				scopes == null ? null : String.join(" ", scopes),
				userId,
				provider,
				providerUserId);
	}

	public Optional<OAuthAccessTokenRecord> findOAuthAccessToken(UUID userId, String provider) {
		return jdbcTemplate.query("""
				SELECT access_token_encrypted, access_token_expires_at, scopes
				FROM oauth_accounts
				WHERE user_id = ? AND provider = ? AND access_token_encrypted IS NOT NULL
				ORDER BY updated_at DESC
				LIMIT 1
				""", this::mapToken, userId, provider).stream().findFirst();
	}

	private Optional<AppUser> findByOAuthAccount(String provider, String providerUserId) {
		return jdbcTemplate.query("""
				SELECT u.id, u.email, u.display_name, u.avatar_url
				FROM users u
				JOIN oauth_accounts oa ON oa.user_id = u.id
				WHERE oa.provider = ? AND oa.provider_user_id = ?
				""", this::mapUser, provider, providerUserId).stream().findFirst();
	}

	private AppUser merge(AppUser existing, OAuthProfile profile) {
		return new AppUser(
				existing.id(),
				prefer(profile.email(), existing.email()),
				prefer(profile.displayName(), existing.displayName()),
				prefer(profile.avatarUrl(), existing.avatarUrl()));
	}

	private String prefer(String current, String previous) {
		return current == null || current.isBlank() ? previous : current;
	}

	private void insertUser(AppUser user) {
		jdbcTemplate.update("""
				INSERT INTO users (id, email, display_name, avatar_url)
				VALUES (?, ?, ?, ?)
				""", user.id(), user.email(), user.displayName(), user.avatarUrl());
	}

	private void updateUser(AppUser user) {
		jdbcTemplate.update("""
				UPDATE users
				SET email = ?, display_name = ?, avatar_url = ?, updated_at = CURRENT_TIMESTAMP
				WHERE id = ?
				""", user.email(), user.displayName(), user.avatarUrl(), user.id());
	}

	private void insertOAuthAccount(UUID id, UUID userId, OAuthProfile profile) {
		jdbcTemplate.update("""
				INSERT INTO oauth_accounts (id, user_id, provider, provider_user_id, email, display_name, avatar_url)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				userId,
				profile.provider(),
				profile.providerUserId(),
				profile.email(),
				profile.displayName(),
				profile.avatarUrl());
	}

	private void updateOAuthAccount(UUID userId, OAuthProfile profile) {
		jdbcTemplate.update("""
				UPDATE oauth_accounts
				SET email = ?, display_name = ?, avatar_url = ?, updated_at = CURRENT_TIMESTAMP
				WHERE user_id = ? AND provider = ? AND provider_user_id = ?
				""",
				profile.email(),
				profile.displayName(),
				profile.avatarUrl(),
				userId,
				profile.provider(),
				profile.providerUserId());
	}

	private AppUser mapUser(ResultSet rs, int rowNum) throws SQLException {
		return new AppUser(
				rs.getObject("id", UUID.class),
				rs.getString("email"),
				rs.getString("display_name"),
				rs.getString("avatar_url"));
	}

	private OAuthAccessTokenRecord mapToken(ResultSet rs, int rowNum) throws SQLException {
		return new OAuthAccessTokenRecord(
				rs.getString("access_token_encrypted"),
				rs.getObject("access_token_expires_at", OffsetDateTime.class),
				rs.getString("scopes"));
	}
}
