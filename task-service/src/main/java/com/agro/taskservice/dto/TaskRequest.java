package com.agro.taskservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /task}. {@code terrain_id} es obligatorio (no hay
 * parcels en main; decision documentada en LLM-WORK/04-task-service-from-main).
 */
public record TaskRequest(
        @NotBlank(message = "{task.type.required}")
        String task_type_code,
        @NotNull(message = "{task.terrain.required}")
        UUID terrain_id,
        @NotNull(message = "{task.planned.required}")
        @Future(message = "{task.planned.past}")
        LocalDateTime planned_at,
        @NotNull(message = "{task.duration.required}")
        @Positive(message = "{task.duration.positive}")
        Integer estimated_duration_minutes,
        @NotNull(message = "{task.assigned.required}")
        UUID assigned_to,
        @Valid
        List<PlannedInput> planned_inputs,
        String notes,
        @Valid
        RecurrenceSpec recurrence
) {
}
