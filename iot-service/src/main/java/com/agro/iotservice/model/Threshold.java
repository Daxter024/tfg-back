package com.agro.iotservice.model;

import com.agro.iotservice.constants.VariableKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Threshold rule: either sensor-scoped (sensor_id != null, variable == null)
 * or variable-scoped (sensor_id == null, variable != null) — enforced by the
 * SQL XOR check. Per-sensor wins over per-variable at evaluation time.
 */
public record Threshold(
        UUID id,
        UUID sensor_id,
        VariableKind variable,
        BigDecimal min_value,
        BigDecimal max_value,
        List<UUID> notify_user_ids,
        Instant created_at
) {
}
