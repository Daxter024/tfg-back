package com.agro.inputservice.dto;

import com.agro.inputservice.model.MovementKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload de registro manual de un movimiento. {@code reason=TASK} o
 * {@code TASK_REVERT} solo los usa el listener interno; el service los rechaza
 * si llegan desde REST.
 */
public record MovementRequest(
        @NotNull MovementKind kind,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @PastOrPresent LocalDate occurred_at,
        @NotBlank String reason,
        String notes
) {
}
