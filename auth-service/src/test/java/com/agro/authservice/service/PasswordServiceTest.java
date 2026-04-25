package com.agro.authservice.service;

import com.agro.authservice.dto.ChangePasswordRequestDTO;
import com.agro.authservice.dto.ForgotPasswordRequestDTO;
import com.agro.authservice.dto.ResetPasswordRequestDTO;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.exception.InvalidPasswordResetException;
import com.agro.authservice.exception.PasswordMismatchException;
import com.agro.authservice.exception.SamePasswordException;
import com.agro.authservice.model.PasswordResetToken;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.PasswordResetTokenRepository;
import com.agro.authservice.repository.UserRepository;
import com.agro.authservice.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditLogService auditLogService;
    @Mock private MailService mailService;
    @Mock private I18nService i18nService;

    private PasswordService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PasswordService(userRepository, resetRepository, passwordEncoder,
                refreshTokenService, auditLogService, mailService, i18nService,
                "http://localhost:3000");
        // Default: any i18n key resolves to a non-null sentinel so Mockito's
        // anyString() matchers don't reject null returns. We stub via Answer
        // because the second overload uses varargs and a plain any() doesn't
        // cover all arities.
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("a@b.c");
        u.setFull_name("Ana");
        u.setPassword("OLD_HASH");
        return u;
    }

    @Test
    void change_ok_revokesRefreshAuditsAndNotifies() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("Current1A", "OLD_HASH")).thenReturn(true);
        when(passwordEncoder.matches("New1Pass1", "OLD_HASH")).thenReturn(false);
        when(passwordEncoder.encode("New1Pass1")).thenReturn("NEW_HASH");

        service.change(USER_ID,
                new ChangePasswordRequestDTO("Current1A", "New1Pass1", "New1Pass1"),
                "1.1.1.1");

        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(auditLogService).log(eq("PASSWORD_CHANGED"), eq(USER_ID), eq(USER_ID),
                eq(null), eq(null), eq("1.1.1.1"));
        verify(mailService).send(eq("a@b.c"), anyString(), anyString());
    }

    @Test
    void change_mismatchConfirmation_throws400() {
        when(i18nService.getMessage("user.validation.password.mismatch")).thenReturn("nope");

        assertThatThrownBy(() -> service.change(USER_ID,
                new ChangePasswordRequestDTO("a", "Abc12345", "Different"), null))
                .isInstanceOf(PasswordMismatchException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void change_wrongCurrent_throws401() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(i18nService.getMessage("auth.invalid.credentials")).thenReturn("nope");

        assertThatThrownBy(() -> service.change(USER_ID,
                new ChangePasswordRequestDTO("bad", "Abc12345", "Abc12345"), null))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void change_samePassword_throws400() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("Same12345", "OLD_HASH")).thenReturn(true);
        when(i18nService.getMessage("user.password.same")).thenReturn("same");

        assertThatThrownBy(() -> service.change(USER_ID,
                new ChangePasswordRequestDTO("Same12345", "Same12345", "Same12345"), null))
                .isInstanceOf(SamePasswordException.class);
    }

    @Test
    void forgot_unknownEmail_doesNotPersistToken_butStillRunsDummyHash() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        service.forgot(new ForgotPasswordRequestDTO("ghost@x.com"), null);

        verify(passwordEncoder).matches(eq("dummy"), anyString());
        verify(resetRepository, never()).save(any());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
        verify(auditLogService, never()).log(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void forgot_existingEmail_persistsHashedTokenAndAudits() {
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user()));

        service.forgot(new ForgotPasswordRequestDTO("a@b.c"), "8.8.8.8");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser_id()).isEqualTo(USER_ID);
        assertThat(saved.getUsed_at()).isNull();
        assertThat(saved.getExpires_at()).isAfter(Instant.now().plusSeconds(60));
        assertThat(saved.getToken_hash()).hasSize(64);
        verify(mailService).send(eq("a@b.c"), anyString(), anyString());
        verify(auditLogService).log(eq("PASSWORD_RESET_REQUESTED"), eq(USER_ID), eq(USER_ID),
                eq(null), any(), eq("8.8.8.8"));
    }

    @Test
    void reset_okFlow_marksTokenUsedAndRevokesRefresh() {
        String plain = "abcd1234";
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUser_id(USER_ID);
        token.setToken_hash(TokenHasher.sha256(plain));
        token.setExpires_at(Instant.now().plusSeconds(600));
        when(resetRepository.findByToken_hash(TokenHasher.sha256(plain))).thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("New1Pass1", "OLD_HASH")).thenReturn(false);
        when(passwordEncoder.encode("New1Pass1")).thenReturn("NEW_HASH");

        service.reset(new ResetPasswordRequestDTO(plain, "New1Pass1", "New1Pass1"), null);

        assertThat(token.getUsed_at()).isNotNull();
        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(auditLogService).log(eq("PASSWORD_RESET"), eq(USER_ID), eq(USER_ID),
                eq(null), eq(null), any());
        verify(mailService).send(eq("a@b.c"), anyString(), anyString());
    }

    @Test
    void reset_expiredToken_throws400() {
        String plain = "abcd1234";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken_hash(TokenHasher.sha256(plain));
        token.setExpires_at(Instant.now().minusSeconds(60));
        when(resetRepository.findByToken_hash(anyString())).thenReturn(Optional.of(token));
        when(i18nService.getMessage("user.password.reset.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.reset(
                new ResetPasswordRequestDTO(plain, "Abc12345", "Abc12345"), null))
                .isInstanceOf(InvalidPasswordResetException.class);
    }

    @Test
    void reset_alreadyUsedToken_throws400() {
        String plain = "abcd1234";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken_hash(TokenHasher.sha256(plain));
        token.setExpires_at(Instant.now().plusSeconds(60));
        token.setUsed_at(Instant.now());
        when(resetRepository.findByToken_hash(anyString())).thenReturn(Optional.of(token));
        when(i18nService.getMessage("user.password.reset.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.reset(
                new ResetPasswordRequestDTO(plain, "Abc12345", "Abc12345"), null))
                .isInstanceOf(InvalidPasswordResetException.class);
    }

    @Test
    void reset_unknownToken_throws400() {
        when(resetRepository.findByToken_hash(anyString())).thenReturn(Optional.empty());
        when(i18nService.getMessage("user.password.reset.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.reset(
                new ResetPasswordRequestDTO("nope", "Abc12345", "Abc12345"), null))
                .isInstanceOf(InvalidPasswordResetException.class);
    }

    @Test
    void reset_samePassword_throws400() {
        String plain = "abcd1234";
        PasswordResetToken token = new PasswordResetToken();
        token.setUser_id(USER_ID);
        token.setToken_hash(TokenHasher.sha256(plain));
        token.setExpires_at(Instant.now().plusSeconds(600));
        when(resetRepository.findByToken_hash(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("Same12345", "OLD_HASH")).thenReturn(true);
        when(i18nService.getMessage("user.password.same")).thenReturn("same");

        assertThatThrownBy(() -> service.reset(
                new ResetPasswordRequestDTO(plain, "Same12345", "Same12345"), null))
                .isInstanceOf(SamePasswordException.class);
    }
}
