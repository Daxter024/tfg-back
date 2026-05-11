package com.agro.iotservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produced on the {@code sensor-alert} topic when a brand-new alert row is
 * inserted (NOT on reading_count++). task-service is the configured
 * consumer (its SensorAlertListener already has the matching type-mapping
 * for {@code com.agro.iotservice.event.SensorAlertEvent}).
 *
 * <p>Payload mirrors plan §7.3 exactly — including {@code terrainId} as the
 * scope identifier (no land-subdivision id is used anywhere in this
 * service). Decision D5: notifyUserIds is decided here and consumed
 * verbatim by the hub.</p>
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
