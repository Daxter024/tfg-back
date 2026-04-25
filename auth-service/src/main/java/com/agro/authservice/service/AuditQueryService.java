package com.agro.authservice.service;

import com.agro.authservice.dto.AuditEntryDTO;
import com.agro.authservice.model.AuditLog;
import com.agro.authservice.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryDTO> search(String action, UUID target, UUID actor,
                                      Instant from, Instant to, Pageable pageable) {
        return repository.search(blankToNull(action), target, actor, from, to, pageable)
                .map(this::toDto);
    }

    private AuditEntryDTO toDto(AuditLog entry) {
        return new AuditEntryDTO(
                entry.getId(),
                entry.getActor_user_id(),
                entry.getAction(),
                entry.getTarget_user_id(),
                entry.getBefore_value(),
                entry.getAfter_value(),
                entry.getIp(),
                entry.getCreated_at()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
