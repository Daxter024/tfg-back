package com.agro.authservice.service;

import com.agro.authservice.exception.InvalidRefreshTokenException;
import com.agro.authservice.model.RefreshToken;
import com.agro.authservice.repository.RefreshTokenRepository;
import com.agro.authservice.util.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final I18nService i18nService;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               I18nService i18nService,
                               @Value("${jwt.refresh-token.ttl-days:7}") long ttlDays) {
        this.repository = repository;
        this.i18nService = i18nService;
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Transactional
    public Issued issue(UUID userId) {
        String plain = generatePlainToken();
        RefreshToken entity = new RefreshToken();
        entity.setId(UUID.randomUUID());
        entity.setUser_id(userId);
        entity.setToken_hash(TokenHasher.sha256(plain));
        entity.setCreated_at(Instant.now());
        entity.setExpires_at(Instant.now().plus(ttl));
        repository.save(entity);
        return new Issued(plain, entity);
    }

    @Transactional
    public RefreshToken consumeAndRotate(String plainToken) {
        RefreshToken existing = repository.findByToken_hash(TokenHasher.sha256(plainToken))
                .orElseThrow(() -> new InvalidRefreshTokenException(i18nService.getMessage("auth.refresh.invalid")));

        if (existing.getRevoked_at() != null || existing.getExpires_at().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException(i18nService.getMessage("auth.refresh.invalid"));
        }

        existing.setRevoked_at(Instant.now());
        repository.save(existing);
        return existing;
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllActiveByUser(userId, Instant.now());
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record Issued(String plain, RefreshToken entity) {
    }
}
