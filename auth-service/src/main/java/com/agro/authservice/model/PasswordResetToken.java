package com.agro.authservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID user_id;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String token_hash;

    @Column(name = "expires_at", nullable = false)
    private Instant expires_at;

    @Column(name = "used_at")
    private Instant used_at;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant created_at;
}
