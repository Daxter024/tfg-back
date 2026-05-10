package com.agro.seasonservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonRequest(
        @NotNull(message = "{season.terrain.required}")
        UUID terrain_id,

        @NotNull(message = "{season.crop.required}")
        UUID crop_id,

        @NotNull(message = "{season.start.required}")
        LocalDate start_date,

        LocalDate end_date,

        Integer season_type_id,

        @Size(max = 2000, message = "{season.observations.size}")
        String observations
) {
    @AssertTrue(message = "{season.dates.range}")
    public boolean isEndAfterStart() {
        return end_date == null || start_date == null || !end_date.isBefore(start_date);
    }
}
