package com.agro.authservice.repository;

import com.agro.authservice.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select p from PasswordResetToken p where p.token_hash = :hash")
    Optional<PasswordResetToken> findByToken_hash(@Param("hash") String hash);
}
