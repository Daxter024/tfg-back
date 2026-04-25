package com.agro.authservice.repository;

import com.agro.authservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select r from RefreshToken r where r.token_hash = :hash")
    Optional<RefreshToken> findByToken_hash(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshToken r set r.revoked_at = :now where r.user_id = :userId and r.revoked_at is null")
    int revokeAllActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
