package com.agro.authservice.service;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.exception.AccountInactiveException;
import com.agro.authservice.exception.AccountLockedException;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.UserRepository;
import com.agro.authservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleService roleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private I18nService i18nService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RevokedTokenService revokedTokenService;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest httpRequest;

    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "ana@example.com";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleService, passwordEncoder, jwtUtil, i18nService,
                refreshTokenService, revokedTokenService, auditLogService, 60L);
    }

    private User activeUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail(EMAIL);
        u.setPassword("HASH");
        u.setRole_id(3);
        u.setStatus("active");
        u.setFailed_login_attempts(0);
        return u;
    }

    @Test
    void login_ok_resetsCounter_setsLastLogin_emitsTokensAndAudits() {
        User user = activeUser();
        user.setFailed_login_attempts(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("good", "HASH")).thenReturn(true);
        when(roleService.getRoleName(3)).thenReturn("agricultor");
        UUID jti = UUID.randomUUID();
        when(jwtUtil.generateAccessToken(USER_ID, EMAIL, "agricultor"))
                .thenReturn(new JwtUtil.AccessToken("access.jwt", jti, Instant.now().plusSeconds(3600)));
        when(refreshTokenService.issue(USER_ID))
                .thenReturn(new RefreshTokenService.Issued("plain-refresh", null));

        LoginResponseDTO result = authService.authenticate(new LoginRequestDTO(EMAIL, "good"), httpRequest);

        assertThat(result.token()).isEqualTo("access.jwt");
        assertThat(result.refresh_token()).isEqualTo("plain-refresh");
        assertThat(result.expires_in_seconds()).isEqualTo(3600);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getFailed_login_attempts()).isZero();
        assertThat(saved.getLocked_until()).isNull();
        assertThat(saved.getLast_login_at()).isNotNull();

        verify(auditLogService).log(eq("LOGIN_SUCCESS"), eq(USER_ID), eq(USER_ID), eq(null), any(), any());
    }

    @Test
    void login_badPassword_incrementsCounter_doesNotLock_below5() {
        User user = activeUser();
        user.setFailed_login_attempts(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(i18nService.getMessage("auth.invalid.credentials")).thenReturn("bad");

        assertThatThrownBy(() ->
                authService.authenticate(new LoginRequestDTO(EMAIL, "wrong"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getFailed_login_attempts()).isEqualTo(3);
        assertThat(saved.getLocked_until()).isNull();
        verify(auditLogService).log(eq("LOGIN_FAILED"), eq(USER_ID), eq(USER_ID), eq(null), any(), any());
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void login_fifthBadPassword_locksFor15min_andResetsCounter() {
        User user = activeUser();
        user.setFailed_login_attempts(4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(i18nService.getMessage("auth.invalid.credentials")).thenReturn("bad");

        assertThatThrownBy(() ->
                authService.authenticate(new LoginRequestDTO(EMAIL, "wrong"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getLocked_until()).isNotNull();
        assertThat(saved.getLocked_until())
                .isAfter(Instant.now().plus(14, ChronoUnit.MINUTES))
                .isBefore(Instant.now().plus(16, ChronoUnit.MINUTES));
        assertThat(saved.getFailed_login_attempts()).isZero();
    }

    @Test
    void login_lockedAccount_throwsAccountLocked_andDoesNotCheckPassword() {
        User user = activeUser();
        user.setLocked_until(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(i18nService.getMessage(eq("auth.account.locked"), any())).thenReturn("locked");

        assertThatThrownBy(() ->
                authService.authenticate(new LoginRequestDTO(EMAIL, "good"), httpRequest))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_inactiveAccount_throwsAccountInactive() {
        User user = activeUser();
        user.setStatus("inactive");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(i18nService.getMessage("auth.account.inactive")).thenReturn("inactive");

        assertThatThrownBy(() ->
                authService.authenticate(new LoginRequestDTO(EMAIL, "good"), httpRequest))
                .isInstanceOf(AccountInactiveException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_expiredLock_isCleanedOnSuccess() {
        User user = activeUser();
        user.setLocked_until(Instant.now().minus(1, ChronoUnit.MINUTES));
        user.setFailed_login_attempts(3);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("good", "HASH")).thenReturn(true);
        when(roleService.getRoleName(3)).thenReturn("agricultor");
        when(jwtUtil.generateAccessToken(any(), anyString(), anyString()))
                .thenReturn(new JwtUtil.AccessToken("a", UUID.randomUUID(), Instant.now().plusSeconds(60)));
        when(refreshTokenService.issue(USER_ID))
                .thenReturn(new RefreshTokenService.Issued("r", null));

        authService.authenticate(new LoginRequestDTO(EMAIL, "good"), httpRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLocked_until()).isNull();
        assertThat(captor.getValue().getFailed_login_attempts()).isZero();
    }
}
