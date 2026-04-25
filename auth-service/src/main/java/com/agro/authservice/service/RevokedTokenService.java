package com.agro.authservice.service;

import com.agro.authservice.model.RevokedToken;
import com.agro.authservice.repository.RevokedTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RevokedTokenService {

    private final RevokedTokenRepository repository;

    public RevokedTokenService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(UUID jti, UUID userId, Instant expiresAt) {
        if (repository.existsByJti(jti)) {
            return;
        }
        RevokedToken entry = new RevokedToken(jti, userId, Instant.now(), expiresAt);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(UUID jti) {
        return repository.existsByJti(jti);
    }
}
