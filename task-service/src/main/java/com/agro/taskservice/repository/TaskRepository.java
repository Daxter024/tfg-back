package com.agro.taskservice.repository;

import com.agro.taskservice.model.Task;
import com.agro.taskservice.model.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio del agregado Task. Sigue el patron season-service: JdbcTemplate
 * con SQL escrito a mano, sin JPA.
 *
 * <p>HU-TAR-01..04 anaden mas metodos sobre este skeleton.</p>
 */
@Repository
@RequiredArgsConstructor
public class TaskRepository {

    private final JdbcTemplate jdbcTemplate;

    /* ---------------------------- task_type ---------------------------- */

    public List<TaskType> findAllTypes() {
        return jdbcTemplate.query(
                "SELECT id, code, label_key FROM task_type ORDER BY id",
                taskTypeMapper());
    }

    public Optional<TaskType> findTypeByCode(String code) {
        try {
            TaskType t = jdbcTemplate.queryForObject(
                    "SELECT id, code, label_key FROM task_type WHERE code = ?",
                    taskTypeMapper(), code);
            return Optional.ofNullable(t);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /* ------------------------------- task ------------------------------ */

    public Optional<Task> findById(UUID id) {
        try {
            Task t = jdbcTemplate.queryForObject(
                    "SELECT * FROM task WHERE id = ?",
                    taskMapper(), id);
            return Optional.ofNullable(t);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> findByIdProjected(UUID id, String selectClause) {
        return jdbcTemplate.queryForMap(
                "SELECT " + selectClause + " FROM task WHERE id = ?", id);
    }

    /* --------------------------- row mappers --------------------------- */

    static RowMapper<TaskType> taskTypeMapper() {
        return (rs, n) -> new TaskType(
                rs.getInt("id"),
                rs.getString("code"),
                rs.getString("label_key"));
    }

    static RowMapper<Task> taskMapper() {
        return (rs, n) -> new Task(
                rs.getObject("id", java.util.UUID.class),
                rs.getInt("task_type_id"),
                rs.getObject("terrain_id", java.util.UUID.class),
                rs.getTimestamp("planned_at") == null ? null : rs.getTimestamp("planned_at").toLocalDateTime(),
                (Integer) rs.getObject("estimated_duration_minutes"),
                rs.getString("state"),
                tsToLdt(rs.getTimestamp("started_at")),
                tsToLdt(rs.getTimestamp("finished_at")),
                (Integer) rs.getObject("real_duration_minutes"),
                rs.getObject("created_by", java.util.UUID.class),
                rs.getObject("assigned_to", java.util.UUID.class),
                rs.getObject("recurrence_parent_id", java.util.UUID.class),
                rs.getString("recurrence_rule"),
                rs.getString("notes"),
                rs.getString("planned_inputs"),
                rs.getString("consumed_inputs"),
                tsToLdt(rs.getTimestamp("created_at")),
                tsToLdt(rs.getTimestamp("updated_at")));
    }

    private static java.time.LocalDateTime tsToLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
