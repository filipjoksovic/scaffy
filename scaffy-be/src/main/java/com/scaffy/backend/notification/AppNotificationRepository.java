package com.scaffy.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppNotificationRepository {

	private final JdbcTemplate jdbcTemplate;

	public AppNotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public AppNotification create(
			UUID userId,
			UUID workspaceId,
			String type,
			String title,
			String message,
			String targetUrl) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO app_notifications (id, user_id, workspace_id, type, title, message, target_url)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, userId, workspaceId, type, title, message, targetUrl);
		return findById(userId, id);
	}

	public List<AppNotification> listUnread(UUID userId) {
		return jdbcTemplate.query("""
				SELECT id, user_id, workspace_id, type, title, message, target_url, read_at, created_at
				FROM app_notifications
				WHERE user_id = ? AND read_at IS NULL
				ORDER BY created_at DESC
				LIMIT 20
				""", this::map, userId);
	}

	public void markRead(UUID userId, UUID id) {
		jdbcTemplate.update("""
				UPDATE app_notifications
				SET read_at = CURRENT_TIMESTAMP
				WHERE user_id = ? AND id = ?
				""", userId, id);
	}

	private AppNotification findById(UUID userId, UUID id) {
		return jdbcTemplate.query("""
				SELECT id, user_id, workspace_id, type, title, message, target_url, read_at, created_at
				FROM app_notifications
				WHERE user_id = ? AND id = ?
				""", this::map, userId, id).stream().findFirst().orElseThrow();
	}

	private AppNotification map(ResultSet rs, int rowNum) throws SQLException {
		return new AppNotification(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getString("type"),
				rs.getString("title"),
				rs.getString("message"),
				rs.getString("target_url"),
				rs.getObject("read_at", OffsetDateTime.class),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
