package com.agro.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(
        @NotBlank(message = "{auth.refresh.required}") String refresh_token
) {
}
