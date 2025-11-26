package com.agro.cropservice.model;

import java.util.UUID;

public record Crop(
        UUID id, String name, String description, Integer crop_type_id
) {
}
