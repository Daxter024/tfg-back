package com.agro.iotservice.dto;

import com.agro.iotservice.constants.SensorStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * PATCH body for /sensor/{id}. All fields optional — only the provided ones
 * are applied (null = leave unchanged).
 */
public record SensorUpdateRequest(
        @Size(max = 100) String external_id,
        @Size(max = 16) String unit,
        @Positive Integer expected_interval_seconds,
        SensorStatus status
) {
}
