package com.agro.inputservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento de stock. Inmutable: nunca se actualizan ni borran (para
 * borrar usar soft-delete de input, pero esto bloquearia con
 * {@code ON DELETE RESTRICT}; los movimientos persisten para trazabilidad).
 */
public record InputMovement(
        UUID id,
        UUID input_id,
        MovementKind kind,
        BigDecimal quantity,
        LocalDate occurred_at,
        UUID task_id,
        UUID performed_by,
        String reason,
        String notes,
        Instant created_at
) {
}
