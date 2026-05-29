package com.scaffy.backend.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthInstanceRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ProviderTokenCrypto providerTokenCrypto;

	public OAuthInstanceRepository(JdbcTemplate jdbcTemplate, ProviderTokenCrypto providerTokenCrypto) {
		this.jdbcTemplate = jdbcTemplate;
		this.providerTokenCrypto = providerTokenCrypto;
	}

	public OAuthInstance upsertByHost(String baseUrl, String host, String displayName, String clientId,
			String clientSecret) {
		String registrationId = OAuthInstance.registrationIdForHost(host);
		String encryptedSecret = providerTokenCrypto.encrypt(clientSecret);
		int updated = jdbcTemplate.update("""
				UPDATE oauth_instances
				SET base_url = ?, registration_id = ?, display_name = ?, client_id = ?,
					client_secret_encrypted = ?, updated_at = CURRENT_TIMESTAMP
				WHERE host = ?
				""", baseUrl, registrationId, displayName, clientId, encryptedSecret, host);
		if (updated == 0) {
			jdbcTemplate.update("""
					INSERT INTO oauth_instances
						(id, registration_id, provider, base_url, host, display_name, client_id, client_secret_encrypted)
					VALUES (?, ?, 'gitlab', ?, ?, ?, ?, ?)
					""", UUID.randomUUID(), registrationId, baseUrl, host, displayName, clientId, encryptedSecret);
		}
		return findByRegistrationId(registrationId)
				.orElseThrow(() -> new IllegalStateException("Instance disappeared after upsert: " + registrationId));
	}

	public Optional<OAuthInstance> findByRegistrationId(String registrationId) {
		return jdbcTemplate.query("""
				SELECT id, registration_id, provider, base_url, host, display_name, client_id, client_secret_encrypted
				FROM oauth_instances
				WHERE registration_id = ?
				""", this::mapInstance, registrationId).stream().findFirst();
	}

	public List<OAuthInstanceSummary> listPublic() {
		return jdbcTemplate.query("""
				SELECT registration_id, host, display_name
				FROM oauth_instances
				ORDER BY host
				""", (rs, rowNum) -> new OAuthInstanceSummary(
				rs.getString("registration_id"),
				rs.getString("host"),
				rs.getString("display_name")));
	}

	private OAuthInstance mapInstance(ResultSet rs, int rowNum) throws SQLException {
		return new OAuthInstance(
				rs.getObject("id", UUID.class),
				rs.getString("registration_id"),
				rs.getString("provider"),
				rs.getString("base_url"),
				rs.getString("host"),
				rs.getString("display_name"),
				rs.getString("client_id"),
				providerTokenCrypto.decrypt(rs.getString("client_secret_encrypted")));
	}
}
