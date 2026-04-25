package com.agro.authservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actor_user_id;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_user_id")
    private UUID target_user_id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    private Map<String, Object> before_value;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    private Map<String, Object> after_value;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant created_at;
}
