package com.agro.iotservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body of POST /ingest/sensor/{id}/reading. Always non-empty to avoid no-op
 * round-trips that would still touch the auth filter.
 */
public record ReadingBatchRequest(
        @NotEmpty(message = "{reading.batch.empty}") List<@Valid Reading> readings
) {
}
