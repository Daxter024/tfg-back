package com.agro.inputservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento {@code stock-low} producido por input-service y consumido por
 * task-service (hub D5). {@code createdBy} es el destinatario de la notif —
 * pre-calculado aqui para no obligar al hub a resolverlo con auth-service.
 *
 * <p>El consumidor en task-service tiene type-mapping
 * {@code com.agro.inputservice.event.StockLowEvent ->
 *  com.agro.taskservice.event.StockLowEvent} (mismo shape).</p>
 */
public record StockLowEvent(
        UUID inputId,
        String inputName,
        BigDecimal currentStock,
        BigDecimal threshold,
        String unit,
        UUID createdBy
) {
}
