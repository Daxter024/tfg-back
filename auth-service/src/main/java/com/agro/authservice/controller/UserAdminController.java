package com.agro.authservice.controller;

import com.agro.authservice.dto.AdminCreateUserDTO;
import com.agro.authservice.dto.AdminUpdateUserDTO;
import com.agro.authservice.dto.UserDetailDTO;
import com.agro.authservice.dto.UserSummaryDTO;
import com.agro.authservice.service.AuthContextResolver;
import com.agro.authservice.service.UserAdminService;
import com.agro.authservice.util.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserAdminController {

    private final UserAdminService service;
    private final AuthContextResolver authContextResolver;

    public UserAdminController(UserAdminService service, AuthContextResolver authContextResolver) {
        this.service = service;
        this.authContextResolver = authContextResolver;
    }

    @Operation(summary = "List users (admin only)")
    @GetMapping
    public ResponseEntity<Page<UserSummaryDTO>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        authContextResolver.resolveAdmin(authHeader);
        return ResponseEntity.ok(service.list(q, role, status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Get user by id (admin only)")
    @GetMapping("/{id}")
    public ResponseEntity<UserDetailDTO> get(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id
    ) {
        authContextResolver.resolveAdmin(authHeader);
        return ResponseEntity.ok(service.get(id));
    }

    @Operation(summary = "Create user (admin only)")
    @PostMapping
    public ResponseEntity<UserDetailDTO> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid AdminCreateUserDTO body,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolveAdmin(authHeader);
        UUID id = service.create(body, ctx.userId(), extractIp(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.get(id));
    }

    @Operation(summary = "Replace user (admin only). PUT semantics: send all fields.")
    @PutMapping("/{id}")
    public ResponseEntity<UserDetailDTO> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody @Valid AdminUpdateUserDTO body,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolveAdmin(authHeader);
        UserDetailDTO updated = service.update(id, body, ctx.userId(), extractIp(request));
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Soft-delete (logical) user (admin only)")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolveAdmin(authHeader);
        service.deactivate(id, ctx.userId(), extractIp(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate user (admin only)")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolveAdmin(authHeader);
        service.reactivate(id, ctx.userId(), extractIp(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hard-delete user (admin only). Emits user-deleted event")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolveAdmin(authHeader);
        service.delete(id, ctx.userId(), extractIp(request));
        return ResponseEntity.noContent().build();
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
