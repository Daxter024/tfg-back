package com.agro.authservice.service;

import com.agro.authservice.model.AuditLog;
import com.agro.authservice.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action,
                    UUID actorUserId,
                    UUID targetUserId,
                    Map<String, Object> beforeValue,
                    Map<String, Object> afterValue,
                    String ip) {
        AuditLog entry = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor_user_id(actorUserId)
                .action(action)
                .target_user_id(targetUserId)
                .before_value(beforeValue)
                .after_value(afterValue)
                .ip(ip)
                .created_at(Instant.now())
                .build();
        repository.save(entry);
    }
}
