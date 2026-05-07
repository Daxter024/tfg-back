package com.agro.terrainservice.repository;

import com.agro.terrainservice.model.Attachment;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AttachmentRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Attachment> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Attachment(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("terrain_id"),
            rs.getString("original_name"),
            rs.getString("mime_type"),
            rs.getLong("size_bytes"),
            rs.getString("storage_key"),
            (UUID) rs.getObject("uploaded_by"),
            timestampToInstant(rs, "uploaded_at")
    );

    private static java.time.Instant timestampToInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    public UUID insert(UUID terrainId, String originalName, String mimeType, long sizeBytes,
                       String storageKey, UUID uploadedBy) {
        String sql = """
                INSERT INTO attachment (terrain_id, original_name, mime_type, size_bytes, storage_key, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, UUID.class,
                terrainId, originalName, mimeType, sizeBytes, storageKey, uploadedBy);
    }

    public Optional<Attachment> findById(UUID id) {
        String sql = "SELECT * FROM attachment WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Attachment> findByTerrainId(UUID terrainId) {
        String sql = "SELECT * FROM attachment WHERE terrain_id = ? ORDER BY uploaded_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, terrainId);
    }

    public long sumSizeByTerrainId(UUID terrainId) {
        String sql = "SELECT COALESCE(SUM(size_bytes), 0) FROM attachment WHERE terrain_id = ?";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, terrainId);
        return total == null ? 0L : total;
    }

    public int deleteById(UUID id) {
        String sql = "DELETE FROM attachment WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }
}
