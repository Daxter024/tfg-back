package com.agro.iotservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code sensor_reading}. Used for raw queries.
 */
public record SensorReading(
        UUID sensor_id,
        Instant recorded_at,
        BigDecimal value
) {
}
