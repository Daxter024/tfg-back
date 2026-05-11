package com.agro.iotservice.dto;

import com.agro.iotservice.constants.VariableKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of POST /sensor. {@code expected_interval_seconds} defaults to 300
 * (5 min) when null; {@code external_id} is optional and acts as a unique
 * hardware identifier (MAC, IMEI...).
 */
public record SensorRequest(
        @Size(max = 100) String external_id,
        @NotNull VariableKind variable,
        @NotBlank @Size(max = 16) String unit,
        @NotNull UUID terrain_id,
        @Positive Integer expected_interval_seconds
) {
}
