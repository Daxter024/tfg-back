package com.agro.terrainservice.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Modelo de un adjunto (HU-TER-03). No es {@code @Entity} porque toda la
 * persistencia del servicio va por {@code JdbcTemplate}.
 */
public record Attachment(
        UUID id,
        UUID terrain_id,
        String original_name,
        String mime_type,
        long size_bytes,
        String storage_key,
        UUID uploaded_by,
        Instant uploaded_at
) {
}
