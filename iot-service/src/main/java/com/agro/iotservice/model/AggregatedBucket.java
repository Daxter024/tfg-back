package com.agro.iotservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A row from {@code sensor_reading_hourly} or {@code sensor_reading_daily}.
 * Used by the agg=hourly/agg=daily reading endpoints.
 */
public record AggregatedBucket(
        UUID sensor_id,
        Instant bucket,
        BigDecimal avg_value,
        BigDecimal min_value,
        BigDecimal max_value,
        Long samples
) {
}
