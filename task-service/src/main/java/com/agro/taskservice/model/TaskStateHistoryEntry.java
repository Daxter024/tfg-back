package com.agro.taskservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskStateHistoryEntry(
        UUID id,
        UUID task_id,
        String from_state,
        String to_state,
        UUID changed_by,
        LocalDateTime changed_at,
        String note
) {
}
