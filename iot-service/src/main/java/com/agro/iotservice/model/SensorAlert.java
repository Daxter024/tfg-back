package com.agro.iotservice.model;

import com.agro.iotservice.constants.AlertKind;
import com.agro.iotservice.constants.AlertState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One alert row. {@code reading_count} grows while an open (new|reviewed)
 * alert keeps receiving out-of-range readings; Kafka is published only on the
 * INSERT, not on subsequent updates (plan §7.2).
 */
public record SensorAlert(
        UUID id,
        UUID sensor_id,
        UUID threshold_id,
        AlertKind kind,
        BigDecimal first_value,
        Instant first_recorded_at,
        Instant last_recorded_at,
        int reading_count,
        AlertState state,
        String comment,
        UUID reviewed_by,
        Instant reviewed_at,
        Instant resolved_at
) {
}
