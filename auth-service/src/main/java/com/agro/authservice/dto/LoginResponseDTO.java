package com.agro.authservice.dto;

public record LoginResponseDTO(String token, String refresh_token, long expires_in_seconds) {
}
