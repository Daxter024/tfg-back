package com.agro.terrainservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un adjunto que se devuelve por API. {@code download_url} es relativa
 * y el cliente la concatena al host del gateway (no incluimos host aqui para no
 * acoplarnos a la URL publica).
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
