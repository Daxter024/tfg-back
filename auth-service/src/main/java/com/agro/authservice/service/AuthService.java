package com.agro.authservice.service;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.exception.AccountInactiveException;
import com.agro.authservice.exception.AccountLockedException;
import com.agro.authservice.exception.EmailNotFoundException;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.exception.InvalidRefreshTokenException;
import com.agro.authservice.model.RefreshToken;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.UserRepository;
import com.agro.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final I18nService i18nService;
    private final RefreshTokenService refreshTokenService;
    private final RevokedTokenService revokedTokenService;
    private final AuditLogService auditLogService;
    private final long accessTtlSeconds;

    public AuthService(UserRepository userRepository,
                       RoleService roleService,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       I18nService i18nService,
                       RefreshTokenService refreshTokenService,
                       RevokedTokenService revokedTokenService,
                       AuditLogService auditLogService,
                       @Value("${jwt.access-token.ttl-minutes:60}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.i18nService = i18nService;
        this.refreshTokenService = refreshTokenService;
        this.revokedTokenService = revokedTokenService;
        this.auditLogService = auditLogService;
        this.accessTtlSeconds = Duration.ofMinutes(ttlMinutes).toSeconds();
    }

    @Transactional
    public LoginResponseDTO authenticate(LoginRequestDTO loginRequest, HttpServletRequest request) {
        String ip = extractIp(request);

        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> {
                    auditLogService.log("LOGIN_FAILED", null, null,
                            null, Map.of("email", loginRequest.email(), "reason", "email_not_found"), ip);
                    return new EmailNotFoundException(i18nService.getMessage("auth.email.not.found"));
                });

        if ("inactive".equals(user.getStatus())) {
            auditLogService.log("LOGIN_FAILED", user.getId(), user.getId(),
                    null, Map.of("reason", "inactive"), ip);
            throw new AccountInactiveException(i18nService.getMessage("auth.account.inactive"));
        }

        Instant now = Instant.now();
        if (user.getLocked_until() != null && user.getLocked_until().isAfter(now)) {
            long secondsLeft = Duration.between(now, user.getLocked_until()).toSeconds();
            auditLogService.log("LOGIN_FAILED", user.getId(), user.getId(),
                    null, Map.of("reason", "locked", "seconds_left", secondsLeft), ip);
            throw new AccountLockedException(i18nService.getMessage("auth.account.locked", secondsLeft));
        }

        if (!passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            int attempts = user.getFailed_login_attempts() + 1;
            user.setFailed_login_attempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked_until(now.plus(LOCK_DURATION));
                user.setFailed_login_attempts(0);
            }
            userRepository.save(user);
            auditLogService.log("LOGIN_FAILED", user.getId(), user.getId(),
                    null, Map.of("reason", "bad_password", "attempts", attempts), ip);
            throw new InvalidCredentialsException(i18nService.getMessage("auth.invalid.credentials"));
        }

        user.setFailed_login_attempts(0);
        user.setLocked_until(null);
        user.setLast_login_at(now);
        userRepository.save(user);

        JwtUtil.AccessToken access = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                roleService.getRoleName(user.getRole_id()));
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ip", ip);
        after.put("jti", access.jti().toString());
        auditLogService.log("LOGIN_SUCCESS", user.getId(), user.getId(), null, after, ip);

        return new LoginResponseDTO(access.token(), refresh.plain(), accessTtlSeconds);
    }

    @Transactional
    public LoginResponseDTO refresh(String plainRefreshToken, HttpServletRequest request) {
        RefreshToken consumed = refreshTokenService.consumeAndRotate(plainRefreshToken);

        User user = userRepository.findById(consumed.getUser_id())
                .orElseThrow(() -> new InvalidRefreshTokenException(i18nService.getMessage("auth.refresh.invalid")));

        if ("inactive".equals(user.getStatus())) {
            throw new InvalidRefreshTokenException(i18nService.getMessage("auth.refresh.invalid"));
        }

        JwtUtil.AccessToken access = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                roleService.getRoleName(user.getRole_id()));
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId());

        auditLogService.log("TOKEN_REFRESHED", user.getId(), user.getId(),
                null, Map.of("jti", access.jti().toString()), extractIp(request));

        return new LoginResponseDTO(access.token(), refresh.plain(), accessTtlSeconds);
    }

    @Transactional
    public void logout(String accessToken, HttpServletRequest request) {
        Claims claims = jwtUtil.parseClaims(accessToken);
        UUID jti = UUID.fromString(claims.getId());
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        Instant expiresAt = claims.getExpiration().toInstant();

        revokedTokenService.revoke(jti, userId, expiresAt);
        refreshTokenService.revokeAllForUser(userId);

        auditLogService.log("LOGOUT", userId, userId, null,
                Map.of("jti", jti.toString()), extractIp(request));
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        Claims claims = jwtUtil.parseClaims(token);
        String jtiStr = claims.getId();
        if (jtiStr == null) {
            return false;
        }
        return !revokedTokenService.isRevoked(UUID.fromString(jtiStr));
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
