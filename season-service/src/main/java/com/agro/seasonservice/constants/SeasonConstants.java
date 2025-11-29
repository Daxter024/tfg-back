package com.agro.seasonservice.constants;

import java.util.Set;

public class SeasonConstants {
    public static final Set<String> SEASON_ALLOWED_FIELDS = Set.of(
            "id", "terrain_id", "crop_id", "start_date", "end_date", "season_type_id", "observations"
    );

    public static final Set<String> SEASON_TYPE_ALLOWED_FIELDS = Set.of(
            "id", "name", "description"
    );
}
