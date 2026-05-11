package com.agro.iotservice.model;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain record for sensor + latest derived state. {@code lastValue} and
 * {@code inRange} are joined-in projections that may be null when not
 * computed.
 */
public record Sensor(
        UUID id,
        String external_id,
        VariableKind variable,
        String unit,
        UUID terrain_id,
        Integer expected_interval_seconds,
        SensorStatus status,
        UUID created_by,
        Instant created_at,
        Instant last_reading_at,
        BigDecimal last_value,
        Boolean in_range
) {
}
