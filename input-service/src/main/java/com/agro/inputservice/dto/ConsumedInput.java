package com.agro.inputservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Forma parte del payload {@code TaskCompletedEvent} consumido de task-service.
 * Los nombres de los campos en snake_case coinciden con los del productor
 * ({@code task-service/.../dto/ConsumedInput.java}) — la deserializacion JSON
 * se basa en esos nombres.
 *
 * <p>Si {@code input_id} es null el insumo se introdujo como entrada libre y
 * no entra en stock (queda solo como anotacion en la tarea).</p>
 */
public record ConsumedInput(
        String input_name,
        UUID input_id,
        BigDecimal quantity,
        String unit
) {
}
