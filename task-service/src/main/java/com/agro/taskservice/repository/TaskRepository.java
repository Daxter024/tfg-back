package com.agro.taskservice.repository;

import com.agro.taskservice.dto.TaskSummaryDTO;
import com.agro.taskservice.model.Task;
import com.agro.taskservice.model.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio del agregado Task. JdbcTemplate + SQL escrito a mano (patron
 * season-service). Sin JPA, sin Spring Data.
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

    public Optional<TaskType> findTypeById(Integer id) {
        try {
            TaskType t = jdbcTemplate.queryForObject(
                    "SELECT id, code, label_key FROM task_type WHERE id = ?",
                    taskTypeMapper(), id);
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

    public boolean hasStateHistory(UUID taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_state_history WHERE task_id = ?",
                Integer.class, taskId);
        return count != null && count > 0;
    }

    public UUID insert(Task task) {
        String sql = """
                INSERT INTO task (task_type_id, terrain_id, planned_at,
                                  estimated_duration_minutes, state, created_by,
                                  assigned_to, recurrence_parent_id, recurrence_rule,
                                  notes, planned_inputs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, UUID.class,
                task.task_type_id(),
                task.terrain_id(),
                Timestamp.valueOf(task.planned_at()),
                task.estimated_duration_minutes(),
                task.state() == null ? "PENDING" : task.state(),
                task.created_by(),
                task.assigned_to(),
                task.recurrence_parent_id(),
                task.recurrence_rule(),
                task.notes(),
                task.planned_inputs());
    }

    public int update(UUID id, LocalDateTime plannedAt, Integer estimatedDuration,
                       UUID assignedTo, String notes) {
        return jdbcTemplate.update("""
                UPDATE task
                   SET planned_at                = COALESCE(?, planned_at),
                       estimated_duration_minutes = COALESCE(?, estimated_duration_minutes),
                       assigned_to               = COALESCE(?, assigned_to),
                       notes                     = COALESCE(?, notes),
                       updated_at                = NOW()
                 WHERE id = ?
                """,
                plannedAt == null ? null : Timestamp.valueOf(plannedAt),
                estimatedDuration, assignedTo, notes, id);
    }

    public int delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM task WHERE id = ?", id);
    }

    public int deleteByTerrainId(UUID terrainId) {
        return jdbcTemplate.update("DELETE FROM task WHERE terrain_id = ?", terrainId);
    }

    /* -------------------------- transitions --------------------------- */

    public void applyTransition(UUID id, String newState, LocalDateTime startedAt,
                                LocalDateTime finishedAt, Integer realDuration,
                                String consumedInputsJson) {
        jdbcTemplate.update("""
                UPDATE task
                   SET state                   = ?,
                       started_at              = COALESCE(?, started_at),
                       finished_at             = COALESCE(?, finished_at),
                       real_duration_minutes   = COALESCE(?, real_duration_minutes),
                       consumed_inputs         = COALESCE(?::jsonb, consumed_inputs),
                       updated_at              = NOW()
                 WHERE id = ?
                """,
                newState,
                startedAt == null ? null : Timestamp.valueOf(startedAt),
                finishedAt == null ? null : Timestamp.valueOf(finishedAt),
                realDuration,
                consumedInputsJson,
                id);
    }

    public void insertHistory(UUID taskId, String fromState, String toState,
                               UUID changedBy, LocalDateTime changedAt, String note) {
        jdbcTemplate.update("""
                INSERT INTO task_state_history (task_id, from_state, to_state,
                                                changed_by, changed_at, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                taskId, fromState, toState, changedBy,
                Timestamp.valueOf(changedAt), note);
    }

    /* ---------------------------- dashboard --------------------------- */

    /**
     * Devuelve totales por estado (PENDING, IN_PROGRESS, FINISHED, CANCELLED)
     * + un sintetico OVERDUE para tareas vencidas. Acepta los mismos filtros
     * que el listado (sobre todo {@code terrainIdIn} para role scoping).
     */
    public Map<String, Long> totalsByState(TaskFilters f) {
        String base = "SELECT t.state AS state, COUNT(*) AS c FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id";
        QueryBuild qb = buildFilteredQuery(base, f);
        String sql = qb.sql() + " GROUP BY t.state";
        Map<String, Long> totals = new java.util.LinkedHashMap<>();
        for (String s : List.of("PENDING", "IN_PROGRESS", "FINISHED", "CANCELLED")) {
            totals.put(s, 0L);
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, qb.params().toArray())) {
            totals.put((String) row.get("state"), ((Number) row.get("c")).longValue());
        }
        QueryBuild overdueQb = buildFilteredQuery(
                "SELECT COUNT(*) FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id",
                new TaskFilters(f == null ? null : f.assignedTo(),
                        f == null ? null : f.createdBy(),
                        null, f == null ? null : f.typeCodes(),
                        f == null ? null : f.terrainId(),
                        f == null ? null : f.from(),
                        f == null ? null : f.to(),
                        Boolean.TRUE,
                        f == null ? null : f.terrainIdIn()));
        Long overdue = jdbcTemplate.queryForObject(overdueQb.sql(), Long.class, overdueQb.params().toArray());
        totals.put("OVERDUE", overdue == null ? 0L : overdue);
        return totals;
    }

    public List<Map<String, Object>> countsByWeek(TaskFilters f) {
        String base = """
                SELECT date_trunc('week', t.planned_at)::date AS week_start, COUNT(*) AS c
                  FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id
                """;
        QueryBuild qb = buildFilteredQuery(base, f);
        return jdbcTemplate.queryForList(
                qb.sql() + " GROUP BY 1 ORDER BY 1",
                qb.params().toArray());
    }

    public List<Map<String, Object>> countsByType(TaskFilters f) {
        String base = """
                SELECT tt.code AS task_type_code, COUNT(*) AS c
                  FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id
                """;
        QueryBuild qb = buildFilteredQuery(base, f);
        return jdbcTemplate.queryForList(
                qb.sql() + " GROUP BY 1 ORDER BY 2 DESC",
                qb.params().toArray());
    }

    /* ----------------------------- export ----------------------------- */

    /* ----------------------- scheduler queries ----------------------- */

    /** Tareas PENDING que entran en ventana de upcoming
     *  ({@code planned_at - lead <= now < planned_at}). */
    public List<Map<String, Object>> findUpcomingCandidates(LocalDateTime now) {
        return jdbcTemplate.queryForList("""
                SELECT t.id, tt.code AS task_type_code, t.terrain_id, t.planned_at,
                       t.assigned_to, t.created_by
                  FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id
                 WHERE t.state = 'PENDING'
                   AND t.planned_at >= ?
                """, Timestamp.valueOf(now));
    }

    /** Tareas en estado PENDING/IN_PROGRESS cuya planned_at + duration esta en
     *  el pasado. */
    public List<Map<String, Object>> findOverdueCandidates(LocalDateTime now) {
        return jdbcTemplate.queryForList("""
                SELECT t.id, tt.code AS task_type_code, t.terrain_id, t.planned_at,
                       t.assigned_to, t.created_by
                  FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id
                 WHERE t.state IN ('PENDING','IN_PROGRESS')
                   AND (t.planned_at + (t.estimated_duration_minutes || ' minutes')::interval) < ?
                """, Timestamp.valueOf(now));
    }

    /** Stream de filas para exportar a CSV. Reusa los filtros del listado. */
    public void streamForExport(TaskFilters f, org.springframework.jdbc.core.RowCallbackHandler handler) {
        String base = """
                SELECT t.id, tt.code AS task_type_code, t.terrain_id, t.planned_at,
                       t.state, t.started_at, t.finished_at, t.real_duration_minutes,
                       t.assigned_to, t.created_by
                  FROM task t
                  LEFT JOIN task_type tt ON tt.id = t.task_type_id
                """;
        QueryBuild qb = buildFilteredQuery(base, f);
        jdbcTemplate.query(qb.sql() + " ORDER BY t.planned_at DESC", qb.params().toArray(), handler);
    }

    /* --------------------- D2 user-deleted policy ---------------------- */

    public int deleteByUserIdAndStateIn(UUID userId, Collection<String> states) {
        if (states == null || states.isEmpty()) return 0;
        StringBuilder placeholders = new StringBuilder();
        Object[] params = new Object[states.size() + 2];
        params[0] = userId;
        params[1] = userId;
        int i = 2;
        for (String s : states) {
            if (placeholders.length() > 0) placeholders.append(",");
            placeholders.append("?");
            params[i++] = s;
        }
        return jdbcTemplate.update(
                "DELETE FROM task WHERE (created_by = ? OR assigned_to = ?) AND state IN ("
                        + placeholders + ")",
                params);
    }

    public int anonymizeAssigneeForFinished(UUID userId, UUID placeholder) {
        return jdbcTemplate.update(
                "UPDATE task SET assigned_to = ? WHERE assigned_to = ? AND state = 'FINISHED'",
                placeholder, userId);
    }

    public int anonymizeCreatorForFinished(UUID userId, UUID placeholder) {
        return jdbcTemplate.update(
                "UPDATE task SET created_by = ? WHERE created_by = ? AND state = 'FINISHED'",
                placeholder, userId);
    }

    /* ----------------------- listing + filtering ----------------------- */

    /** Filtros para {@code GET /task}. */
    public record TaskFilters(
            UUID assignedTo,
            UUID createdBy,
            List<String> states,
            List<String> typeCodes,
            UUID terrainId,
            LocalDate from,
            LocalDate to,
            Boolean overdue,
            List<UUID> terrainIdIn /* scoping por rol */
    ) {
    }

    public long countWithFilters(TaskFilters f) {
        QueryBuild qb = buildFilteredQuery("SELECT COUNT(*) FROM task t LEFT JOIN task_type tt ON tt.id = t.task_type_id", f);
        Long n = jdbcTemplate.queryForObject(qb.sql(), Long.class, qb.params().toArray());
        return n == null ? 0L : n;
    }

    public List<TaskSummaryDTO> findWithFilters(TaskFilters f, int page, int size) {
        String base = """
                SELECT t.id, tt.code AS task_type_code, t.terrain_id, t.planned_at,
                       t.estimated_duration_minutes, t.state, t.assigned_to, t.created_by,
                       (t.state IN ('PENDING','IN_PROGRESS')
                        AND (t.planned_at + (t.estimated_duration_minutes || ' minutes')::interval) < NOW())
                       AS overdue
                  FROM task t
                  LEFT JOIN task_type tt ON tt.id = t.task_type_id
                """;
        QueryBuild qb = buildFilteredQuery(base, f);
        String sql = qb.sql() + " ORDER BY t.planned_at DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(qb.params());
        params.add(size);
        params.add((long) page * size);
        return jdbcTemplate.query(sql, summaryMapper(), params.toArray());
    }

    public List<TaskSummaryDTO> findCalendar(LocalDateTime from, LocalDateTime to, TaskFilters f) {
        String base = """
                SELECT t.id, tt.code AS task_type_code, t.terrain_id, t.planned_at,
                       t.estimated_duration_minutes, t.state, t.assigned_to, t.created_by,
                       (t.state IN ('PENDING','IN_PROGRESS')
                        AND (t.planned_at + (t.estimated_duration_minutes || ' minutes')::interval) < NOW())
                       AS overdue
                  FROM task t
                  LEFT JOIN task_type tt ON tt.id = t.task_type_id
                 WHERE t.planned_at >= ? AND t.planned_at < ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(from));
        params.add(Timestamp.valueOf(to));
        QueryBuild qb = appendFilters(base, params, f, false);
        return jdbcTemplate.query(qb.sql() + " ORDER BY t.planned_at ASC", summaryMapper(), qb.params().toArray());
    }

    private record QueryBuild(String sql, List<Object> params) {
    }

    private QueryBuild buildFilteredQuery(String base, TaskFilters f) {
        return appendFilters(base, new ArrayList<>(), f, true);
    }

    private QueryBuild appendFilters(String base, List<Object> params, TaskFilters f, boolean addWhere) {
        StringBuilder sql = new StringBuilder(base);
        List<String> clauses = new ArrayList<>();
        if (f != null) {
            if (f.assignedTo() != null) {
                clauses.add("t.assigned_to = ?");
                params.add(f.assignedTo());
            }
            if (f.createdBy() != null) {
                clauses.add("t.created_by = ?");
                params.add(f.createdBy());
            }
            if (f.states() != null && !f.states().isEmpty()) {
                clauses.add("t.state IN (" + placeholders(f.states().size()) + ")");
                params.addAll(f.states());
            }
            if (f.typeCodes() != null && !f.typeCodes().isEmpty()) {
                clauses.add("tt.code IN (" + placeholders(f.typeCodes().size()) + ")");
                params.addAll(f.typeCodes());
            }
            if (f.terrainId() != null) {
                clauses.add("t.terrain_id = ?");
                params.add(f.terrainId());
            }
            if (f.from() != null) {
                clauses.add("t.planned_at >= ?");
                params.add(Timestamp.valueOf(f.from().atStartOfDay()));
            }
            if (f.to() != null) {
                clauses.add("t.planned_at < ?");
                params.add(Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
            }
            if (Boolean.TRUE.equals(f.overdue())) {
                clauses.add("t.state IN ('PENDING','IN_PROGRESS') AND (t.planned_at + (t.estimated_duration_minutes || ' minutes')::interval) < NOW()");
            }
            if (f.terrainIdIn() != null) {
                if (f.terrainIdIn().isEmpty()) {
                    clauses.add("1 = 0");
                } else {
                    clauses.add("t.terrain_id IN (" + placeholders(f.terrainIdIn().size()) + ")");
                    params.addAll(f.terrainIdIn());
                }
            }
        }
        if (!clauses.isEmpty()) {
            sql.append(addWhere ? " WHERE " : " AND ");
            sql.append(String.join(" AND ", clauses));
        }
        return new QueryBuild(sql.toString(), params);
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
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
                tsToLdt(rs.getTimestamp("planned_at")),
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

    static RowMapper<TaskSummaryDTO> summaryMapper() {
        return (rs, n) -> new TaskSummaryDTO(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("task_type_code"),
                rs.getObject("terrain_id", java.util.UUID.class),
                tsToLdt(rs.getTimestamp("planned_at")),
                (Integer) rs.getObject("estimated_duration_minutes"),
                rs.getString("state"),
                rs.getObject("assigned_to", java.util.UUID.class),
                rs.getObject("created_by", java.util.UUID.class),
                rs.getBoolean("overdue"));
    }

    private static LocalDateTime tsToLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
