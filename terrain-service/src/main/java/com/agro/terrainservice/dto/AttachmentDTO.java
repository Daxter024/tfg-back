package com.agro.terrainservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista pública del adjunto. {@code download_url} se compone en el service
 * a partir del propio id; {@code storage_key} no se expone.
 */
public record AttachmentDTO(
        UUID id,
        UUID terrain_id,
        String original_name,
        String mime_type,
        long size_bytes,
        UUID uploaded_by,
        Instant uploaded_at,
        String download_url
) {
}
