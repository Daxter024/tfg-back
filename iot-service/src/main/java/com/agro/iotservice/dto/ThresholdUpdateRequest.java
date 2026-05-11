package com.agro.iotservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * PATCH /threshold/{id}. Null leaves the field unchanged; {@code clear_min}
 * / {@code clear_max} explicit booleans allow nullifying a previously-set
 * bound without re-sending the whole record.
 */
public record ThresholdUpdateRequest(
        BigDecimal min_value,
        BigDecimal max_value,
        List<UUID> notify_user_ids,
        Boolean clear_min,
        Boolean clear_max
) {
}
