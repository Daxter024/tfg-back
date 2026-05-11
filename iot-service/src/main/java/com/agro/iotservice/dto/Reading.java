package com.agro.iotservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single sensor reading. Used both inside ReadingBatchRequest and as the
 * canonical input type for {@link com.agro.iotservice.ingestor.ReadingIngestor}.
 */
public record Reading(
        @NotNull @PastOrPresent Instant recorded_at,
        @NotNull BigDecimal value
) {
}
