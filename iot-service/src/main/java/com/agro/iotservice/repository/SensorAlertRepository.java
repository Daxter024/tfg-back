package com.agro.iotservice.repository;

import com.agro.iotservice.constants.AlertKind;
import com.agro.iotservice.constants.AlertState;
import com.agro.iotservice.model.SensorAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SensorAlertRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<SensorAlert> rowMapper = (rs, n) -> new SensorAlert(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("sensor_id"),
            (UUID) rs.getObject("threshold_id"),
            AlertKind.valueOf(rs.getString("kind")),
            rs.getBigDecimal("first_value"),
            rs.getTimestamp("first_recorded_at").toInstant(),
            rs.getTimestamp("last_recorded_at").toInstant(),
            rs.getInt("reading_count"),
            AlertState.fromDb(rs.getString("state")),
            rs.getString("comment"),
            (UUID) rs.getObject("reviewed_by"),
            rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant()
    );

    public Optional<SensorAlert> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM sensor_alert WHERE id = ?", rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Finds any open (state IN new|reviewed) alert for the (sensor, kind)
     * pair. Used by the dedup logic so out-of-range bursts grow
     * reading_count on the same row.
     */
    public Optional<SensorAlert> findOpenByKind(UUID sensorId, AlertKind kind) {
        try {
            SensorAlert row = jdbc.queryForObject("""
                            SELECT * FROM sensor_alert
                             WHERE sensor_id = ?
                               AND kind = ?::alert_kind
                               AND state <> 'resolved'
                             ORDER BY first_recorded_at DESC
                             LIMIT 1
                            """,
                    rowMapper, sensorId, kind.name());
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns ALL open alerts (any kind) for a sensor. Used to auto-resolve
     * pending alerts when a reading falls back inside the threshold band.
     */
    public List<SensorAlert> findOpenForSensor(UUID sensorId) {
        return jdbc.query("""
                SELECT * FROM sensor_alert
                 WHERE sensor_id = ?
                   AND state <> 'resolved'
                """, rowMapper, sensorId);
    }

    public UUID insert(UUID sensorId, UUID thresholdId, AlertKind kind,
                       BigDecimal value, Instant recordedAt) {
        return jdbc.queryForObject("""
                        INSERT INTO sensor_alert
                            (sensor_id, threshold_id, kind, first_value,
                             first_recorded_at, last_recorded_at)
                        VALUES (?, ?, ?::alert_kind, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class, sensorId, thresholdId, kind.name(), value,
                Timestamp.from(recordedAt), Timestamp.from(recordedAt));
    }

    /**
     * Bumps reading_count and slides last_recorded_at forward. Returns the
     * new count (so the caller can react if needed) or 0 if nothing
     * matched.
     */
    public int incrementCount(UUID alertId, Instant lastRecordedAt) {
        return jdbc.update("""
                UPDATE sensor_alert
                   SET reading_count = reading_count + 1,
                       last_recorded_at = ?
                 WHERE id = ?
                """, Timestamp.from(lastRecordedAt), alertId);
    }

    public int markReviewed(UUID id, UUID reviewer, String comment) {
        return jdbc.update("""
                UPDATE sensor_alert
                   SET state = 'reviewed',
                       reviewed_by = ?,
                       reviewed_at = NOW(),
                       comment = COALESCE(?, comment)
                 WHERE id = ? AND state = 'new'
                """, reviewer, comment, id);
    }

    public int markResolved(UUID id) {
        return jdbc.update("""
                UPDATE sensor_alert
                   SET state = 'resolved',
                       resolved_at = NOW()
                 WHERE id = ? AND state <> 'resolved'
                """, id);
    }

    public int autoResolveOpenForSensor(UUID sensorId) {
        return jdbc.update("""
                UPDATE sensor_alert
                   SET state = 'resolved',
                       resolved_at = NOW()
                 WHERE sensor_id = ?
                   AND state <> 'resolved'
                """, sensorId);
    }

    public List<SensorAlert> search(AlertState state, UUID terrainId, Instant from, Instant to) {
        // terrain filter is by JOIN through sensor — we keep iot db; ok.
        StringBuilder sql = new StringBuilder("""
                SELECT a.*
                  FROM sensor_alert a
                  JOIN sensor s ON s.id = a.sensor_id
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (state != null) {
            sql.append(" AND a.state = ?::alert_state");
            args.add(state.dbValue());
        }
        if (terrainId != null) {
            sql.append(" AND s.terrain_id = ?");
            args.add(terrainId);
        }
        if (from != null) {
            sql.append(" AND a.first_recorded_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND a.first_recorded_at < ?");
            args.add(Timestamp.from(to));
        }
        sql.append(" ORDER BY a.first_recorded_at DESC LIMIT 500");
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }
}
