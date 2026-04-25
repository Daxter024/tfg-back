package com.agro.authservice.util;

import java.util.UUID;

public record AuthContext(UUID userId, String email, String role) {

    public boolean isAdministrator() {
        return "administrador".equals(role);
    }
}
