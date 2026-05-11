package com.agro.taskservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento {@code stock-low} producido por input-service. La type-mapping de
 * Kafka traduce {@code com.agro.inputservice.event.StockLowEvent} a este tipo.
 *
 * <p>{@code createdBy} es el dueno del insumo — destinatario de la
 * notificacion. El input-service no decide si vale la pena notificar; ese
 * juicio lo hace el hub (anti-spam 24h en NotificationService).</p>
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
