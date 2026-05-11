package com.agro.taskservice.repository;

import com.agro.taskservice.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public UUID create(UUID userId, UUID taskId, String sourceKind, UUID sourceRef,
                       String channel, String title, String body) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notification (user_id, task_id, source_kind, source_ref,
                                          channel, title, body)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, userId, taskId, sourceKind, sourceRef, channel, title, body);
    }

    public List<Notification> findInbox(UUID userId, int page, int size) {
        return jdbcTemplate.query("""
                SELECT id, user_id, task_id, source_kind, source_ref, channel,
                       title, body, created_at, read_at
                  FROM notification
                 WHERE user_id = ?
                 ORDER BY created_at DESC
                 LIMIT ? OFFSET ?
                """, mapper(), userId, size, (long) page * size);
    }

    public long unreadCount(UUID userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
        return n == null ? 0L : n;
    }

    public int markRead(UUID id, UUID userId) {
        return jdbcTemplate.update(
                "UPDATE notification SET read_at = NOW() WHERE id = ? AND user_id = ? AND read_at IS NULL",
                id, userId);
    }

    public int markAllRead(UUID userId) {
        return jdbcTemplate.update(
                "UPDATE notification SET read_at = NOW() WHERE user_id = ? AND read_at IS NULL",
                userId);
    }

    public Optional<Notification> findById(UUID id) {
        try {
            Notification n = jdbcTemplate.queryForObject("""
                    SELECT id, user_id, task_id, source_kind, source_ref, channel,
                           title, body, created_at, read_at
                      FROM notification WHERE id = ?
                    """, mapper(), id);
            return Optional.ofNullable(n);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByTaskKind(UUID taskId, String sourceKind, UUID userId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification
                 WHERE task_id = ? AND source_kind = ? AND user_id = ?
                """, Integer.class, taskId, sourceKind, userId);
        return n != null && n > 0;
    }

    public boolean existsByTaskKindToday(UUID taskId, String sourceKind, UUID userId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification
                 WHERE task_id = ? AND source_kind = ? AND user_id = ?
                   AND created_at::date = CURRENT_DATE
                """, Integer.class, taskId, sourceKind, userId);
        return n != null && n > 0;
    }

    public int countOverdueToday(UUID userId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification
                 WHERE user_id = ? AND source_kind = 'TASK_OVERDUE'
                   AND created_at::date = CURRENT_DATE
                """, Integer.class, userId);
        return n == null ? 0 : n;
    }

    public int deleteOverdueToday(UUID userId) {
        return jdbcTemplate.update("""
                DELETE FROM notification
                 WHERE user_id = ? AND source_kind = 'TASK_OVERDUE'
                   AND created_at::date = CURRENT_DATE
                """, userId);
    }

    public int deleteByUserId(UUID userId) {
        return jdbcTemplate.update("DELETE FROM notification WHERE user_id = ?", userId);
    }

    /** Numero de emisiones de {@code sourceKind/sourceRef} para un user
     *  desde {@code since}. */
    public int countEmissionsSince(String sourceKind, UUID sourceRef, UUID userId, LocalDateTime since) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification_emission_log
                 WHERE source_kind = ? AND source_ref = ? AND user_id = ? AND emitted_at >= ?
                """, Integer.class, sourceKind, sourceRef, userId, Timestamp.valueOf(since));
        return n == null ? 0 : n;
    }

    public void logEmission(String sourceKind, UUID sourceRef, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO notification_emission_log (source_kind, source_ref, user_id)
                VALUES (?, ?, ?)
                """, sourceKind, sourceRef, userId);
    }

    private static RowMapper<Notification> mapper() {
        return (rs, n) -> new Notification(
                rs.getObject("id", java.util.UUID.class),
                rs.getObject("user_id", java.util.UUID.class),
                rs.getObject("task_id", java.util.UUID.class),
                rs.getString("source_kind"),
                rs.getObject("source_ref", java.util.UUID.class),
                rs.getString("channel"),
                rs.getString("title"),
                rs.getString("body"),
                tsToLdt(rs.getTimestamp("created_at")),
                tsToLdt(rs.getTimestamp("read_at")));
    }

    private static LocalDateTime tsToLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
