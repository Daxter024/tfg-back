package com.agro.cropservice.dto;

import java.util.UUID;

public record CropDetailsDTO(
        UUID id, String name, String description, Integer crop_type_id, String crop_type_name
) {
}
