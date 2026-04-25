package com.agro.authservice.service;

import com.agro.authservice.exception.InvalidRefreshTokenException;
import com.agro.authservice.model.RefreshToken;
import com.agro.authservice.repository.RefreshTokenRepository;
import com.agro.authservice.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository repository;
    @Mock private I18nService i18nService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, i18nService, 7L);
    }

    @Test
    void issue_persistsHashedTokenWithExpiry() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshTokenService.Issued issued = service.issue(userId);

        assertThat(issued.plain()).hasSize(64);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser_id()).isEqualTo(userId);
        assertThat(saved.getToken_hash()).isEqualTo(TokenHasher.sha256(issued.plain()));
        assertThat(saved.getExpires_at()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(saved.getRevoked_at()).isNull();
    }

    @Test
    void consumeAndRotate_marksRevokedOnce() {
        String plain = "abc";
        RefreshToken existing = new RefreshToken();
        existing.setId(UUID.randomUUID());
        existing.setUser_id(UUID.randomUUID());
        existing.setToken_hash(TokenHasher.sha256(plain));
        existing.setCreated_at(Instant.now());
        existing.setExpires_at(Instant.now().plusSeconds(60));
        when(repository.findByToken_hash(TokenHasher.sha256(plain))).thenReturn(Optional.of(existing));
        when(repository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken consumed = service.consumeAndRotate(plain);

        assertThat(consumed.getRevoked_at()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void consumeAndRotate_expiredToken_throws() {
        String plain = "abc";
        RefreshToken existing = new RefreshToken();
        existing.setToken_hash(TokenHasher.sha256(plain));
        existing.setExpires_at(Instant.now().minusSeconds(60));
        when(repository.findByToken_hash(anyString())).thenReturn(Optional.of(existing));
        when(i18nService.getMessage("auth.refresh.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.consumeAndRotate(plain))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void consumeAndRotate_revokedToken_throws() {
        String plain = "abc";
        RefreshToken existing = new RefreshToken();
        existing.setToken_hash(TokenHasher.sha256(plain));
        existing.setExpires_at(Instant.now().plusSeconds(60));
        existing.setRevoked_at(Instant.now());
        when(repository.findByToken_hash(anyString())).thenReturn(Optional.of(existing));
        when(i18nService.getMessage("auth.refresh.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.consumeAndRotate(plain))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void consumeAndRotate_unknownHash_throws() {
        when(repository.findByToken_hash(anyString())).thenReturn(Optional.empty());
        when(i18nService.getMessage("auth.refresh.invalid")).thenReturn("invalid");

        assertThatThrownBy(() -> service.consumeAndRotate("anything"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
