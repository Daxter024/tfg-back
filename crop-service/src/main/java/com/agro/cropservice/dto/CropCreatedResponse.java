package com.agro.cropservice.dto;

import java.util.UUID;

public record CropCreatedResponse(UUID id, String name, String message) {
}
