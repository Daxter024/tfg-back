package com.agro.terrainservice.dto;

import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Petición de creación de terreno. Mantiene compatibilidad con clientes
 * que solo envían `name + user_id + geometry` — los campos descriptivos
 * añadidos por HU-TER-01 son opcionales.
 */
public record TerrainRequest(
        @NotBlank(message = "{terrain.name.required}")
        @Size(max = 255, message = "{terrain.name.size}") String name,

        @NotNull(message = "{terrain.user_id.required}") UUID user_id,

        @NotEmpty(message = "{terrain.geometry.required}") Map<String, Object> geometry,

        SoilType soil_type,

        @DecimalMin(value = "0.0", message = "{terrain.slope.invalid}")
        @DecimalMax(value = "100.0", message = "{terrain.slope.invalid}") Double slope_percent,

        IrrigationType irrigation,

        @Pattern(regexp = "^[0-9A-Z]{14,20}$",
                message = "{cadastral.reference.malformed}") String cadastral_ref
) {
}
