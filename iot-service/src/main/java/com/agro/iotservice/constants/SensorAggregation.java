package com.agro.iotservice.constants;

/**
 * Aggregation level applied to a {@code /sensor/{id}/reading} query. The
 * service picks a sensible default based on (to-from) when the client does not
 * specify {@code agg}.
 */
public enum SensorAggregation {
    raw,
    hourly,
    daily
}
