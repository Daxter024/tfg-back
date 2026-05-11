package com.agro.inputservice.controller;

import com.agro.inputservice.dto.MovementRequest;
import com.agro.inputservice.dto.PageResponse;
import com.agro.inputservice.model.InputMovement;
import com.agro.inputservice.model.MovementKind;
import com.agro.inputservice.service.I18nService;
import com.agro.inputservice.service.MovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de movimientos sobre un insumo concreto.
 */
@RestController
@RequestMapping("/input")
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;
    private final I18nService i18n;

    @GetMapping("/{id}/movement")
    public ResponseEntity<PageResponse<InputMovement>> list(
            @PathVariable UUID id,
            @RequestParam(required = false) MovementKind kind,
            @RequestParam(name = "task_id_not_null", required = false) Boolean taskIdNotNull,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(movementService.search(id, kind, taskIdNotNull, from, to, page, size));
    }

    @PostMapping("/{id}/movement")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID id,
            @Valid @RequestBody MovementRequest body,
            @RequestHeader(name = "X-User-Id") UUID userId) {
        UUID newId = movementService.recordManualMovement(id, body, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", newId, "message", i18n.getMessage("input.movement.registered")));
    }

    @PostMapping("/movement/{movementId}/revert")
    public ResponseEntity<Map<String, Object>> revert(
            @PathVariable UUID movementId,
            @RequestHeader(name = "X-User-Id") UUID userId) {
        UUID newId = movementService.revertMovement(movementId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", newId, "message", i18n.getMessage("input.movement.registered")));
    }
}
