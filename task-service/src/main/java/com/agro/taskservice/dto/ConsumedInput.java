package com.agro.taskservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Insumo realmente consumido durante la ejecucion de una tarea. Forma parte
 * del payload {@code TaskCompletedEvent} que consume input-service.
 */
public record ConsumedInput(
        String input_name,
        UUID input_id,
        BigDecimal quantity,
        String unit
) {
}
