package com.agro.iotservice.dto;

import com.agro.iotservice.constants.VariableKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Body of POST /threshold. The XOR between sensor_id and variable, the
 * "at least one bound" and the min<=max ordering are validated in code and
 * also enforced by the database CHECK constraints in V2.
 */
public record ThresholdRequest(
        UUID sensor_id,
        VariableKind variable,
        BigDecimal min_value,
        BigDecimal max_value,
        List<UUID> notify_user_ids
) {
}
