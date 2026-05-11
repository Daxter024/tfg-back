package com.agro.taskservice.repository;

import com.agro.taskservice.model.TaskEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TaskEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public int countByTaskId(UUID taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_evidence WHERE task_id = ?",
                Integer.class, taskId);
        return count == null ? 0 : count;
    }

    public List<TaskEvidence> findByTaskId(UUID taskId) {
        return jdbcTemplate.query("""
                SELECT id, task_id, original_name, mime_type, size_bytes,
                       storage_key, uploaded_by, uploaded_at
                  FROM task_evidence
                 WHERE task_id = ?
                 ORDER BY uploaded_at DESC
                """, mapper(), taskId);
    }

    public Optional<TaskEvidence> findById(UUID id) {
        try {
            TaskEvidence e = jdbcTemplate.queryForObject("""
                    SELECT id, task_id, original_name, mime_type, size_bytes,
                           storage_key, uploaded_by, uploaded_at
                      FROM task_evidence WHERE id = ?
                    """, mapper(), id);
            return Optional.ofNullable(e);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public UUID insert(UUID taskId, String originalName, String mimeType, long sizeBytes,
                        String storageKey, UUID uploadedBy) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO task_evidence (task_id, original_name, mime_type, size_bytes,
                                           storage_key, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, taskId, originalName, mimeType, sizeBytes, storageKey, uploadedBy);
    }

    public int delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM task_evidence WHERE id = ?", id);
    }

    private static RowMapper<TaskEvidence> mapper() {
        return (rs, n) -> new TaskEvidence(
                rs.getObject("id", java.util.UUID.class),
                rs.getObject("task_id", java.util.UUID.class),
                rs.getString("original_name"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("storage_key"),
                rs.getObject("uploaded_by", java.util.UUID.class),
                tsToLdt(rs.getTimestamp("uploaded_at")));
    }

    private static java.time.LocalDateTime tsToLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
