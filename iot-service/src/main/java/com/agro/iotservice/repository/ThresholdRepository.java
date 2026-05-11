package com.agro.iotservice.repository;

import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.model.Threshold;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ThresholdRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<Threshold> rowMapper = (rs, n) -> new Threshold(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("sensor_id"),
            rs.getString("variable") == null ? null : VariableKind.valueOf(rs.getString("variable")),
            rs.getBigDecimal("min_value"),
            rs.getBigDecimal("max_value"),
            uuidArray(rs.getArray("notify_user_ids")),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()
    );

    private static List<UUID> uuidArray(Array sqlArr) throws SQLException {
        if (sqlArr == null) return List.of();
        Object[] raw = (Object[]) sqlArr.getArray();
        List<UUID> out = new ArrayList<>(raw.length);
        for (Object o : raw) {
            if (o instanceof UUID u) out.add(u);
            else if (o != null) out.add(UUID.fromString(o.toString()));
        }
        return out;
    }

    public Optional<Threshold> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM threshold WHERE id = ?", rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Threshold> search(UUID sensorId, VariableKind variable) {
        StringBuilder sql = new StringBuilder("SELECT * FROM threshold WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (sensorId != null) {
            sql.append(" AND sensor_id = ?");
            args.add(sensorId);
        }
        if (variable != null) {
            sql.append(" AND variable = ?::variable_kind");
            args.add(variable.name());
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    /**
     * Resolution rule: prefer the sensor-scoped threshold; fall back to the
     * variable-scoped one. Returns the first matching threshold per scope
     * (most recent first).
     */
    public Optional<Threshold> resolveForSensor(UUID sensorId, VariableKind variable) {
        List<Threshold> bySensor = jdbc.query(
                "SELECT * FROM threshold WHERE sensor_id = ? ORDER BY created_at DESC LIMIT 1",
                rowMapper, sensorId);
        if (!bySensor.isEmpty()) return Optional.of(bySensor.get(0));
        List<Threshold> byVar = jdbc.query(
                "SELECT * FROM threshold WHERE variable = ?::variable_kind ORDER BY created_at DESC LIMIT 1",
                rowMapper, variable.name());
        return byVar.isEmpty() ? Optional.empty() : Optional.of(byVar.get(0));
    }

    public UUID insert(UUID sensorId, VariableKind variable, BigDecimal min, BigDecimal max,
                       List<UUID> notify) {
        UUID[] arr = notify == null ? new UUID[0] : notify.toArray(new UUID[0]);
        return jdbc.queryForObject("""
                        INSERT INTO threshold (sensor_id, variable, min_value, max_value, notify_user_ids)
                        VALUES (?, ?::variable_kind, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                sensorId,
                variable == null ? null : variable.name(),
                min, max,
                arr);
    }

    public int updatePartial(UUID id, BigDecimal min, BigDecimal max, List<UUID> notify,
                             boolean clearMin, boolean clearMax) {
        StringBuilder sql = new StringBuilder("UPDATE threshold SET id = id");
        List<Object> args = new ArrayList<>();
        if (min != null) {
            sql.append(", min_value = ?");
            args.add(min);
        } else if (clearMin) {
            sql.append(", min_value = NULL");
        }
        if (max != null) {
            sql.append(", max_value = ?");
            args.add(max);
        } else if (clearMax) {
            sql.append(", max_value = NULL");
        }
        if (notify != null) {
            sql.append(", notify_user_ids = ?");
            args.add(notify.toArray(new UUID[0]));
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        return jdbc.update(sql.toString(), args.toArray());
    }

    public int delete(UUID id) {
        return jdbc.update("DELETE FROM threshold WHERE id = ?", id);
    }
}
