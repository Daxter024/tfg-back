package com.agro.taskservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vista "ligera" de una tarea para listados.
 */
public record TaskSummaryDTO(
        UUID id,
        String task_type_code,
        UUID terrain_id,
        LocalDateTime planned_at,
        Integer estimated_duration_minutes,
        String state,
        UUID assigned_to,
        UUID created_by,
        boolean overdue
) {
}
