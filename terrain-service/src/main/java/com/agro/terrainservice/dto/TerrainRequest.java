package com.agro.terrainservice.dto;

import java.util.Map;

/**
 * De momento se queda así porque no es necesario hacer operaciones desde java antes de meterlo en la bbdd
 * Si en un futuro es necesario usar el jackson-datatype-jts
 */

public record TerrainRequest(
        String name,
        Map<String, Object> geometry
) {
}

