package com.agro.iotservice.repository;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.model.Sensor;
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
public class SensorRepository {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE = """
            SELECT id, external_id, variable, unit, terrain_id,
                   expected_interval_seconds, status, created_by, created_at,
                   last_reading_at
              FROM sensor
            """;

    private final RowMapper<Sensor> rowMapper = (rs, n) -> new Sensor(
            (UUID) rs.getObject("id"),
            rs.getString("external_id"),
            VariableKind.valueOf(rs.getString("variable")),
            rs.getString("unit"),
            (UUID) rs.getObject("terrain_id"),
            (Integer) rs.getObject("expected_interval_seconds"),
            SensorStatus.valueOf(rs.getString("status")),
            (UUID) rs.getObject("created_by"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("last_reading_at")),
            null, // last_value populated by SensorService when needed
            null  // in_range populated by SensorService when needed
    );

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public Optional<Sensor> findById(UUID id) {
        try {
            Sensor row = jdbc.queryForObject(SELECT_BASE + " WHERE id = ?", rowMapper, id);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Sensor> search(UUID terrainId, VariableKind variable, SensorStatus status) {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (terrainId != null) {
            sql.append(" AND terrain_id = ?");
            args.add(terrainId);
        }
        if (variable != null) {
            sql.append(" AND variable = ?::variable_kind");
            args.add(variable.name());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    public UUID insert(String externalId, VariableKind variable, String unit,
                       UUID terrainId, int expectedIntervalSeconds, UUID createdBy) {
        return jdbc.queryForObject("""
                        INSERT INTO sensor (external_id, variable, unit, terrain_id,
                                            expected_interval_seconds, created_by)
                        VALUES (?, ?::variable_kind, ?, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                externalId, variable.name(), unit, terrainId, expectedIntervalSeconds, createdBy);
    }

    public int updatePartial(UUID id, String unit, Integer expectedIntervalSeconds,
                             SensorStatus status, String externalId) {
        StringBuilder sql = new StringBuilder("UPDATE sensor SET id = id");
        List<Object> args = new ArrayList<>();
        if (unit != null) {
            sql.append(", unit = ?");
            args.add(unit);
        }
        if (expectedIntervalSeconds != null) {
            sql.append(", expected_interval_seconds = ?");
            args.add(expectedIntervalSeconds);
        }
        if (status != null) {
            sql.append(", status = ?");
            args.add(status.name());
        }
        if (externalId != null) {
            sql.append(", external_id = ?");
            args.add(externalId);
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        return jdbc.update(sql.toString(), args.toArray());
    }

    public int delete(UUID id) {
        return jdbc.update("DELETE FROM sensor WHERE id = ?", id);
    }

    public int deleteByTerrainId(UUID terrainId) {
        return jdbc.update("DELETE FROM sensor WHERE terrain_id = ?", terrainId);
    }

    public int deleteByCreatedBy(UUID userId) {
        return jdbc.update("DELETE FROM sensor WHERE created_by = ?", userId);
    }

    /**
     * Marks sensors as no_signal when their last_reading_at is older than
     * 2 * expected_interval. Used by the scheduled job.
     */
    public int markNoSignalIfStale() {
        return jdbc.update("""
                UPDATE sensor
                   SET status = 'no_signal'
                 WHERE status = 'active'
                   AND last_reading_at IS NOT NULL
                   AND last_reading_at < NOW() - (expected_interval_seconds * 2 * INTERVAL '1 second')
                """);
    }

    /**
     * Updates the last_reading_at and bumps status to active when a fresh
     * reading arrives.
     */
    public int touchOnReading(UUID sensorId, Instant recordedAt) {
        return jdbc.update("""
                UPDATE sensor
                   SET status = 'active',
                       last_reading_at = ?
                 WHERE id = ?
                   AND (last_reading_at IS NULL OR last_reading_at < ?)
                """, Timestamp.from(recordedAt), sensorId, Timestamp.from(recordedAt));
    }

    public Optional<BigDecimal> findLastValue(UUID sensorId) {
        try {
            BigDecimal v = jdbc.queryForObject(
                    "SELECT value FROM sensor_reading WHERE sensor_id = ? " +
                            "ORDER BY recorded_at DESC LIMIT 1",
                    BigDecimal.class, sensorId);
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
