package com.agro.taskservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento {@code sensor-alert} producido por iot-service. La type-mapping de
 * Kafka traduce {@code com.agro.iotservice.event.SensorAlertEvent} a este
 * tipo.
 *
 * <p>{@code notifyUserIds} contiene los destinatarios — iot-service decide a
 * quien le importa el sensor (admin del terreno, agricultor, etc.). El hub
 * (task-service) genera una notif por destinatario y aplica el anti-spam
 * grouping a partir de 5 alertas/h.</p>
 */
public record SensorAlertEvent(
        UUID alertId,
        UUID sensorId,
        UUID terrainId,
        String variable,
        String kind,
        BigDecimal value,
        BigDecimal threshold,
        Instant recordedAt,
        List<UUID> notifyUserIds
) {
}
