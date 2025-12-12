package com.agro.terrainservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * De momento se queda así porque no es necesario hacer operaciones desde java
 * antes de meterlo en la bbdd
 * Si en un futuro es necesario usar el jackson-datatype-jts
 */

public record TerrainRequest(
        @NotBlank(message = "{terrain.name.required}") String name,
        @NotNull(message = "{terrain.user_id.required}") UUID user_id,
        @NotEmpty(message = "{terrain.geometry.required}") Map<String, Object> geometry
) {
}
