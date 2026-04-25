package com.agro.authservice.controller;

import com.agro.authservice.dto.AuditEntryDTO;
import com.agro.authservice.service.AuditQueryService;
import com.agro.authservice.service.AuthContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditQueryService service;
    private final AuthContextResolver authContextResolver;

    public AuditController(AuditQueryService service, AuthContextResolver authContextResolver) {
        this.service = service;
        this.authContextResolver = authContextResolver;
    }

    @Operation(summary = "Search audit log (admin only)")
    @GetMapping
    public ResponseEntity<Page<AuditEntryDTO>> search(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID target,
            @RequestParam(required = false) UUID actor,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        authContextResolver.resolveAdmin(authHeader);
        return ResponseEntity.ok(service.search(action, target, actor, from, to, PageRequest.of(page, size)));
    }
}
