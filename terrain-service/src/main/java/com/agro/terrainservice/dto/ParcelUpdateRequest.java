package com.agro.terrainservice.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body de actualizacion parcial (HU-TER-04, PATCH). Cualquiera de los dos
 * campos puede venir nulo (no se modifica). Si {@code geometry} se cambia,
 * el service recalcula area / centroid via las columnas STORED.
 */
public record ParcelUpdateRequest(
        @Size(max = 255, message = "{terrain.name.too.long}")
        String name,

        Map<String, Object> geometry
) {
}
