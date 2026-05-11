package com.agro.taskservice.repository;

import com.agro.taskservice.model.NotificationPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationPreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<NotificationPreference> findByUserId(UUID userId) {
        try {
            NotificationPreference p = jdbcTemplate.queryForObject("""
                    SELECT user_id, email_enabled, in_app_enabled, default_lead_minutes,
                           task_type_lead_minutes::text AS task_type_lead_minutes,
                           quiet_hours_start, quiet_hours_end, also_notify_creator
                      FROM notification_preference WHERE user_id = ?
                    """, mapper(), userId);
            return Optional.ofNullable(p);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void upsert(UUID userId, boolean email, boolean inApp, int defaultLead,
                       String typeLeadJson, LocalTime quietStart, LocalTime quietEnd,
                       boolean notifyCreator) {
        jdbcTemplate.update("""
                INSERT INTO notification_preference
                      (user_id, email_enabled, in_app_enabled, default_lead_minutes,
                       task_type_lead_minutes, quiet_hours_start, quiet_hours_end,
                       also_notify_creator)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                   SET email_enabled         = EXCLUDED.email_enabled,
                       in_app_enabled        = EXCLUDED.in_app_enabled,
                       default_lead_minutes  = EXCLUDED.default_lead_minutes,
                       task_type_lead_minutes = EXCLUDED.task_type_lead_minutes,
                       quiet_hours_start     = EXCLUDED.quiet_hours_start,
                       quiet_hours_end       = EXCLUDED.quiet_hours_end,
                       also_notify_creator   = EXCLUDED.also_notify_creator
                """, userId, email, inApp, defaultLead, typeLeadJson,
                quietStart == null ? null : Time.valueOf(quietStart),
                quietEnd == null ? null : Time.valueOf(quietEnd),
                notifyCreator);
    }

    public int deleteByUserId(UUID userId) {
        return jdbcTemplate.update("DELETE FROM notification_preference WHERE user_id = ?", userId);
    }

    private static RowMapper<NotificationPreference> mapper() {
        return (rs, n) -> new NotificationPreference(
                rs.getObject("user_id", java.util.UUID.class),
                rs.getBoolean("email_enabled"),
                rs.getBoolean("in_app_enabled"),
                rs.getInt("default_lead_minutes"),
                rs.getString("task_type_lead_minutes"),
                rs.getTime("quiet_hours_start") == null ? null : rs.getTime("quiet_hours_start").toLocalTime(),
                rs.getTime("quiet_hours_end") == null ? null : rs.getTime("quiet_hours_end").toLocalTime(),
                rs.getBoolean("also_notify_creator"));
    }
}
