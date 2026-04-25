package com.agro.authservice.repository;

import com.agro.authservice.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:target is null or a.target_user_id = :target)
              and (:actor is null or a.actor_user_id = :actor)
              and (cast(:from as timestamp) is null or a.created_at >= :from)
              and (cast(:to as timestamp) is null or a.created_at <= :to)
            order by a.created_at desc
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("target") UUID target,
                          @Param("actor") UUID actor,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
