package com.agro.terrainservice.repository;

import com.agro.terrainservice.model.Attachment;
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
public class AttachmentRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Attachment> ATTACHMENT_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("uploaded_at");
        return new Attachment(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("terrain_id")),
                rs.getString("original_name"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("storage_key"),
                UUID.fromString(rs.getString("uploaded_by")),
                ts == null ? null : ts.toInstant()
        );
    };

    public UUID save(UUID terrainId,
                     String originalName,
                     String mimeType,
                     long sizeBytes,
                     String storageKey,
                     UUID uploadedBy) {
        String sql = """
                INSERT INTO attachment (
                    terrain_id, original_name, mime_type,
                    size_bytes, storage_key, uploaded_by
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(
                sql, UUID.class,
                terrainId, originalName, mimeType, sizeBytes, storageKey, uploadedBy
        );
    }

    public List<Attachment> findByTerrainId(UUID terrainId) {
        String sql = """
                SELECT id, terrain_id, original_name, mime_type,
                       size_bytes, storage_key, uploaded_by, uploaded_at
                  FROM attachment
                 WHERE terrain_id = ?
                 ORDER BY uploaded_at DESC
                """;
        return jdbcTemplate.query(sql, ATTACHMENT_MAPPER, terrainId);
    }

    public Optional<Attachment> findById(UUID id) {
        String sql = """
                SELECT id, terrain_id, original_name, mime_type,
                       size_bytes, storage_key, uploaded_by, uploaded_at
                  FROM attachment
                 WHERE id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ATTACHMENT_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
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
