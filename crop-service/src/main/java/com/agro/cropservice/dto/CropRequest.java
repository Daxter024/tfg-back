package com.agro.cropservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CropRequest(
        @NotBlank(message = "{name.notblank}")
        @Size(min = 3, max = 100, message = "{name.size}")
        String name,
        @NotBlank(message = "{description.notblank}")
        @Size(min = 10, max = 500, message = "{description.size}")
        String description,
        @NotNull(message = "{croptype.id.notnull}")
        @Positive(message = "{croptype.id.negative}")
        Integer crop_type_id) {
}
