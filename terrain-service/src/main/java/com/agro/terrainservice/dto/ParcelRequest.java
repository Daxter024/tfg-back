package com.agro.terrainservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body de creacion de una parcela (HU-TER-04).
 *
 * <p>El {@code terrain_id} no va en el body: se toma del path de la URL para
 * evitar que el cliente lo cambie y burle la validacion de propiedad.</p>
 */
public record ParcelRequest(
        @NotBlank(message = "{parcel.name.required}")
        @Size(max = 255, message = "{terrain.name.too.long}")
        String name,

        @NotEmpty(message = "{parcel.geometry.required}")
        Map<String, Object> geometry
) {
}
