package com.agro.authservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryDTO(
        UUID id,
        UUID actor_user_id,
        String action,
        UUID target_user_id,
        Map<String, Object> before_value,
        Map<String, Object> after_value,
        String ip,
        Instant created_at
) {
}
