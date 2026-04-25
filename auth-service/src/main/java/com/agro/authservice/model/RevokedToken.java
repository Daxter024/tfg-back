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
@Table(name = "revoked_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private UUID user_id;

    @Column(name = "revoked_at", nullable = false)
    private Instant revoked_at;

    @Column(name = "expires_at", nullable = false)
    private Instant expires_at;
}
