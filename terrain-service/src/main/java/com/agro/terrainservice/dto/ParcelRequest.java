package com.agro.terrainservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ParcelRequest(
        @NotBlank(message = "{parcel.name.required}")
        @Size(max = 255, message = "{parcel.name.size}") String name,

        @NotNull(message = "{parcel.geometry.required}")
        @NotEmpty(message = "{parcel.geometry.required}") Map<String, Object> geometry
) {
}
