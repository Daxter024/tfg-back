package com.agro.terrainservice.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * PATCH parcial: cualquiera de los dos campos puede ser nulo. Si ambos son
 * nulos el service rechaza la petición con 400.
 */
public record ParcelUpdateRequest(
        @Size(max = 255, message = "{parcel.name.size}") String name,
        Map<String, Object> geometry
) {
}
