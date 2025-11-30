package com.agro.seasonservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonRequest(
        @NotNull(message = "terrain_id is required")
        UUID terrain_id,

        @NotNull(message = "crop_id is required")
        UUID crop_id,

        @NotNull(message = "start_date is required")
        LocalDate start_date,

        LocalDate end_date,
        Integer season_type_id,
        String observations
) {
}
