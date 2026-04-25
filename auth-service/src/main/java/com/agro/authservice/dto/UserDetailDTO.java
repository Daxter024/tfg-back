package com.agro.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDetailDTO(
        UUID id,
        String full_name,
        String email,
        String role,
        String status,
        Instant created_at,
        Instant last_login_at,
        int failed_login_attempts,
        Instant locked_until
) {
}
