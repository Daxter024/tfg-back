package com.agro.authservice.service;

import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.exception.InvalidRefreshTokenException;
import com.agro.authservice.model.RefreshToken;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.UserRepository;
import com.agro.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutAndRefreshTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleService roleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private I18nService i18nService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RevokedTokenService revokedTokenService;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest httpRequest;
    @Mock private Claims claims;

    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleService, passwordEncoder, jwtUtil, i18nService,
                refreshTokenService, revokedTokenService, auditLogService, 60L);
    }

    @Test
    void refresh_ok_consumesOldAndIssuesNewPair() {
        RefreshToken consumed = new RefreshToken();
        consumed.setUser_id(USER_ID);
        when(refreshTokenService.consumeAndRotate("plain")).thenReturn(consumed);

        User user = new User();
        user.setId(USER_ID);
        user.setEmail("a@b.c");
        user.setRole_id(3);
        user.setStatus("active");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleService.getRoleName(3)).thenReturn("agricultor");
        when(jwtUtil.generateAccessToken(USER_ID, "a@b.c", "agricultor"))
                .thenReturn(new JwtUtil.AccessToken("new.access", UUID.randomUUID(), Instant.now().plusSeconds(60)));
        when(refreshTokenService.issue(USER_ID))
                .thenReturn(new RefreshTokenService.Issued("new-refresh", null));

        LoginResponseDTO out = authService.refresh("plain", httpRequest);

        assertThat(out.token()).isEqualTo("new.access");
        assertThat(out.refresh_token()).isEqualTo("new-refresh");
        verify(auditLogService).log(eqStr("TOKEN_REFRESHED"), any(), any(), any(), any(), any());
    }

    @Test
    void refresh_inactiveUser_rejects() {
        RefreshToken consumed = new RefreshToken();
        consumed.setUser_id(USER_ID);
        when(refreshTokenService.consumeAndRotate("plain")).thenReturn(consumed);

        User user = new User();
        user.setId(USER_ID);
        user.setStatus("inactive");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(i18nService.getMessage("auth.refresh.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> authService.refresh("plain", httpRequest))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_revokesAccessJtiAndAllUserRefreshTokens() {
        UUID jti = UUID.randomUUID();
        Date expDate = Date.from(Instant.now().plusSeconds(600));
        when(jwtUtil.parseClaims("access.token")).thenReturn(claims);
        when(claims.getId()).thenReturn(jti.toString());
        when(claims.get("userId", String.class)).thenReturn(USER_ID.toString());
        when(claims.getExpiration()).thenReturn(expDate);

        authService.logout("access.token", httpRequest);

        verify(revokedTokenService).revoke(jti, USER_ID, expDate.toInstant());
        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(auditLogService).log(eqStr("LOGOUT"), any(), any(), any(), any(), any());
    }

    @Test
    void validateToken_revokedJti_returnsFalse() {
        UUID jti = UUID.randomUUID();
        when(jwtUtil.parseClaims("a")).thenReturn(claims);
        when(claims.getId()).thenReturn(jti.toString());
        when(revokedTokenService.isRevoked(jti)).thenReturn(true);

        assertThat(authService.validateToken("a")).isFalse();
    }

    @Test
    void validateToken_freshJti_returnsTrue() {
        UUID jti = UUID.randomUUID();
        when(jwtUtil.parseClaims("a")).thenReturn(claims);
        when(claims.getId()).thenReturn(jti.toString());
        when(revokedTokenService.isRevoked(jti)).thenReturn(false);

        assertThat(authService.validateToken("a")).isTrue();
    }

    private static String eqStr(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }
}
