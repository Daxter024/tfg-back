package com.agro.authservice.repository;

import com.agro.authservice.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    boolean existsByJti(UUID jti);
}
