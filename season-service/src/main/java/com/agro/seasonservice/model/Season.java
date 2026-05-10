package com.agro.seasonservice.model;

import java.time.LocalDate;
import java.util.UUID;

public record Season(
        UUID id,
        UUID terrain_id,
        UUID crop_id,
        LocalDate start_date,
        LocalDate end_date,
        String observations,
        Integer season_type_id
) {
}
