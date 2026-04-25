package com.agro.authservice.service;

import com.agro.authservice.exception.ForbiddenRoleException;
import com.agro.authservice.util.AuthContext;
import com.agro.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthContextResolver {

    private final JwtUtil jwtUtil;
    private final RevokedTokenService revokedTokenService;
    private final I18nService i18nService;

    public AuthContextResolver(JwtUtil jwtUtil,
                               RevokedTokenService revokedTokenService,
                               I18nService i18nService) {
        this.jwtUtil = jwtUtil;
        this.revokedTokenService = revokedTokenService;
        this.i18nService = i18nService;
    }

    public AuthContext resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ForbiddenRoleException(i18nService.getMessage("auth.invalid.token"));
        }
        Claims claims;
        try {
            claims = jwtUtil.parseClaims(authorizationHeader.substring(7));
        } catch (JwtException e) {
            throw new ForbiddenRoleException(i18nService.getMessage("auth.invalid.token"));
        }

        String jtiStr = claims.getId();
        if (jtiStr != null && revokedTokenService.isRevoked(UUID.fromString(jtiStr))) {
            throw new ForbiddenRoleException(i18nService.getMessage("auth.invalid.token"));
        }

        UUID userId = UUID.fromString(claims.get("userId", String.class));
        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        return new AuthContext(userId, email, role);
    }

    public AuthContext resolveAdmin(String authorizationHeader) {
        AuthContext ctx = resolve(authorizationHeader);
        if (!ctx.isAdministrator()) {
            throw new ForbiddenRoleException(i18nService.getMessage("user.admin.required"));
        }
        return ctx;
    }
}
