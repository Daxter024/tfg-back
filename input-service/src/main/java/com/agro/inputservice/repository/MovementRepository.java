package com.agro.inputservice.repository;

import com.agro.inputservice.model.InputMovement;
import com.agro.inputservice.model.MovementKind;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a {@code input_movement}.
 */
@Repository
@RequiredArgsConstructor
public class MovementRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<InputMovement> rowMapper = (rs, n) -> new InputMovement(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("input_id"),
            MovementKind.valueOf(rs.getString("kind")),
            rs.getBigDecimal("quantity"),
            rs.getDate("occurred_at").toLocalDate(),
            (UUID) rs.getObject("task_id"),
            (UUID) rs.getObject("performed_by"),
            rs.getString("reason"),
            rs.getString("notes"),
            toInstant(rs.getTimestamp("created_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public UUID insert(UUID inputId, MovementKind kind, BigDecimal quantity, LocalDate occurredAt,
                       UUID taskId, UUID performedBy, String reason, String notes) {
        return jdbc.queryForObject("""
                        INSERT INTO input_movement (input_id, kind, quantity, occurred_at, task_id, performed_by, reason, notes)
                        VALUES (?, ?::movement_kind, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                inputId, kind.name(), quantity, occurredAt, taskId, performedBy, reason, notes);
    }

    public Optional<InputMovement> findById(UUID id) {
        try {
            InputMovement m = jdbc.queryForObject(
                    "SELECT * FROM input_movement WHERE id = ?",
                    rowMapper, id);
            return Optional.ofNullable(m);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<InputMovement> search(UUID inputId, MovementKind kind, Boolean taskIdNotNull,
                                      LocalDate from, LocalDate to,
                                      int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM input_movement WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (inputId != null) {
            sql.append(" AND input_id = ?");
            args.add(inputId);
        }
        if (kind != null) {
            sql.append(" AND kind = ?::movement_kind");
            args.add(kind.name());
        }
        if (taskIdNotNull != null) {
            sql.append(taskIdNotNull ? " AND task_id IS NOT NULL" : " AND task_id IS NULL");
        }
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND occurred_at <= ?");
            args.add(to);
        }
        sql.append(" ORDER BY occurred_at DESC, created_at DESC OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit);
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    public long count(UUID inputId, MovementKind kind, Boolean taskIdNotNull,
                      LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM input_movement WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (inputId != null) {
            sql.append(" AND input_id = ?");
            args.add(inputId);
        }
        if (kind != null) {
            sql.append(" AND kind = ?::movement_kind");
            args.add(kind.name());
        }
        if (taskIdNotNull != null) {
            sql.append(taskIdNotNull ? " AND task_id IS NOT NULL" : " AND task_id IS NULL");
        }
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND occurred_at <= ?");
            args.add(to);
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0L : c;
    }

    /**
     * Anonymiza los movimientos del user: pone performed_by a NULL y deja una
     * marca en notes. Usado por el listener {@code user-deleted}.
     */
    public int anonymizePerformedBy(UUID userId) {
        return jdbc.update("""
                UPDATE input_movement
                   SET performed_by = NULL,
                       notes = COALESCE(notes, '') || ' [user_anonymized]'
                 WHERE performed_by = ?
                """, userId);
    }
}
