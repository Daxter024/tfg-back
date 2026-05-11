package com.agro.taskservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskEvidence(
        UUID id,
        UUID task_id,
        String original_name,
        String mime_type,
        Long size_bytes,
        String storage_key,
        UUID uploaded_by,
        LocalDateTime uploaded_at
) {
}
