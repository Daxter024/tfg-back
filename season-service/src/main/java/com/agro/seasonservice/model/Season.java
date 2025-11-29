package com.agro.seasonservice.model;

import java.util.Date;
import java.util.UUID;

/*
Add Jakarta in the future
 */

public record Season(
        UUID id,
        UUID terrain_id,
        UUID crop_id,
        Date start_date,
        Date end_date,
        String observations,
        Integer season_type_id
) {
}
