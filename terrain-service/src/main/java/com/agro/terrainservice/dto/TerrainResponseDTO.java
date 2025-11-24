package com.agro.terrainservice.dto;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonFilter("terrainFilter")
public record TerrainResponseDTO(
        UUID id,
        String name,
        String geometry,    // Polygon casted to String
        BigDecimal area_m2,
        BigDecimal perimeter_m,
        String centroid,    // Point casted to String
        Instant created_at,
        Instant updated_at
) {
}
