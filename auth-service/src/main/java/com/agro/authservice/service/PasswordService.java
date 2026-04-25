package com.agro.authservice.service;

import com.agro.authservice.dto.ChangePasswordRequestDTO;
import com.agro.authservice.dto.ForgotPasswordRequestDTO;
import com.agro.authservice.dto.ResetPasswordRequestDTO;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.exception.InvalidPasswordResetException;
import com.agro.authservice.exception.PasswordMismatchException;
import com.agro.authservice.exception.SamePasswordException;
import com.agro.authservice.exception.UserNotFoundException;
import com.agro.authservice.model.PasswordResetToken;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.PasswordResetTokenRepository;
import com.agro.authservice.repository.UserRepository;
import com.agro.authservice.util.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PasswordService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY_HASH =
            "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXY1234";
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final I18nService i18nService;
    private final String frontendUrl;

    public PasswordService(UserRepository userRepository,
                           PasswordResetTokenRepository resetRepository,
                           PasswordEncoder passwordEncoder,
                           RefreshTokenService refreshTokenService,
                           AuditLogService auditLogService,
                           MailService mailService,
                           I18nService i18nService,
                           @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
        this.i18nService = i18nService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void change(UUID actorUserId, ChangePasswordRequestDTO dto, String ip) {
        if (!dto.new_password().equals(dto.new_password_confirmation())) {
            throw new PasswordMismatchException(i18nService.getMessage("user.validation.password.mismatch"));
        }

        User user = userRepository.findById(actorUserId)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));

        if (!passwordEncoder.matches(dto.current_password(), user.getPassword())) {
            throw new InvalidCredentialsException(i18nService.getMessage("auth.invalid.credentials"));
        }

        if (passwordEncoder.matches(dto.new_password(), user.getPassword())) {
            throw new SamePasswordException(i18nService.getMessage("user.password.same"));
        }

        user.setPassword(passwordEncoder.encode(dto.new_password()));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.log("PASSWORD_CHANGED", user.getId(), user.getId(), null, null, ip);

        mailService.send(
                user.getEmail(),
                i18nService.getMessage("user.password.changed.subject"),
                i18nService.getMessage("user.password.changed.notification", user.getFull_name())
        );
    }

    @Transactional
    public void forgot(ForgotPasswordRequestDTO dto, String ip) {
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // Mismo coste aproximado que el caso real para mitigar timing attacks.
            passwordEncoder.matches("dummy", DUMMY_HASH);
            return;
        }

        String plain = generateToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUser_id(user.getId());
        token.setToken_hash(TokenHasher.sha256(plain));
        token.setCreated_at(Instant.now());
        token.setExpires_at(Instant.now().plus(RESET_TTL));
        resetRepository.save(token);

        mailService.send(
                user.getEmail(),
                i18nService.getMessage("user.password.reset.subject"),
                i18nService.getMessage("user.password.reset.body",
                        user.getFull_name(),
                        frontendUrl + "/reset-password?token=" + plain)
        );

        auditLogService.log("PASSWORD_RESET_REQUESTED", user.getId(), user.getId(),
                null, Map.of("email", user.getEmail()), ip);
    }

    @Transactional
    public void reset(ResetPasswordRequestDTO dto, String ip) {
        if (!dto.new_password().equals(dto.new_password_confirmation())) {
            throw new PasswordMismatchException(i18nService.getMessage("user.validation.password.mismatch"));
        }

        PasswordResetToken token = resetRepository.findByToken_hash(TokenHasher.sha256(dto.token()))
                .orElseThrow(() -> new InvalidPasswordResetException(
                        i18nService.getMessage("user.password.reset.invalid")));

        if (token.getUsed_at() != null || token.getExpires_at().isBefore(Instant.now())) {
            throw new InvalidPasswordResetException(i18nService.getMessage("user.password.reset.invalid"));
        }

        User user = userRepository.findById(token.getUser_id())
                .orElseThrow(() -> new InvalidPasswordResetException(
                        i18nService.getMessage("user.password.reset.invalid")));

        if (passwordEncoder.matches(dto.new_password(), user.getPassword())) {
            throw new SamePasswordException(i18nService.getMessage("user.password.same"));
        }

        user.setPassword(passwordEncoder.encode(dto.new_password()));
        userRepository.save(user);

        token.setUsed_at(Instant.now());
        resetRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.log("PASSWORD_RESET", user.getId(), user.getId(), null, null, ip);

        mailService.send(
                user.getEmail(),
                i18nService.getMessage("user.password.changed.subject"),
                i18nService.getMessage("user.password.changed.notification", user.getFull_name())
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
