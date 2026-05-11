package com.agro.taskservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /task/{id}}. Todos los campos son opcionales — solo
 * los presentes se aplican. Las validaciones se ejecutan solo si el campo
 * llega con valor (Spring las omite cuando el campo es null).
 */
public record TaskUpdateRequest(
        @Future(message = "{task.planned.past}")
        LocalDateTime planned_at,
        @Positive(message = "{task.duration.positive}")
        Integer estimated_duration_minutes,
        UUID assigned_to,
        String notes
) {
}
