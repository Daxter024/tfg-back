package com.agro.authservice.service;

import com.agro.authservice.exception.ForbiddenRoleException;
import com.agro.authservice.util.AuthContext;
import com.agro.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthContextResolverTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RevokedTokenService revokedTokenService;
    @Mock private I18nService i18nService;
    @Mock private Claims claims;

    private AuthContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthContextResolver(jwtUtil, revokedTokenService, i18nService);
    }

    @Test
    void resolve_validBearer_returnsContext() {
        UUID userId = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        when(jwtUtil.parseClaims("token")).thenReturn(claims);
        when(claims.getId()).thenReturn(jti.toString());
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.getSubject()).thenReturn("a@b.c");
        when(claims.get("role", String.class)).thenReturn("administrador");
        when(revokedTokenService.isRevoked(jti)).thenReturn(false);

        AuthContext ctx = resolver.resolve("Bearer token");

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.role()).isEqualTo("administrador");
        assertThat(ctx.isAdministrator()).isTrue();
    }

    @Test
    void resolveAdmin_withNonAdminRole_throws() {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.parseClaims("token")).thenReturn(claims);
        when(claims.getId()).thenReturn(null);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn("agricultor");
        when(i18nService.getMessage("user.admin.required")).thenReturn("nope");

        assertThatThrownBy(() -> resolver.resolveAdmin("Bearer token"))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void resolve_revokedJti_throws() {
        UUID jti = UUID.randomUUID();
        when(jwtUtil.parseClaims("t")).thenReturn(claims);
        when(claims.getId()).thenReturn(jti.toString());
        when(revokedTokenService.isRevoked(jti)).thenReturn(true);
        when(i18nService.getMessage("auth.invalid.token")).thenReturn("invalid");

        assertThatThrownBy(() -> resolver.resolve("Bearer t"))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void resolve_missingHeader_throws() {
        when(i18nService.getMessage("auth.invalid.token")).thenReturn("invalid");
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void resolve_invalidJwt_throws() {
        when(jwtUtil.parseClaims("bad")).thenThrow(new JwtException("nope"));
        when(i18nService.getMessage("auth.invalid.token")).thenReturn("invalid");

        assertThatThrownBy(() -> resolver.resolve("Bearer bad"))
                .isInstanceOf(ForbiddenRoleException.class);
    }
}
