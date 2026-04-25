package com.agro.terrainservice.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Modelo plano de adjunto, leído/escrito directamente con JdbcTemplate.
 * Usamos {@code record} porque no hay relaciones JPA — el `terrain_id`
 * es FK SQL real (misma BBDD), pero no se navega como objeto.
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
