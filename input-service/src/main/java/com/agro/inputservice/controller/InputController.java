package com.agro.inputservice.controller;

import com.agro.inputservice.dto.InputRequest;
import com.agro.inputservice.dto.InputUpdateRequest;
import com.agro.inputservice.dto.PageResponse;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import com.agro.inputservice.service.I18nService;
import com.agro.inputservice.service.InputService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints CRUD del catalogo {@code input}. Todas las rutas asumen que el
 * gateway aplica {@code StripPrefix=1} sobre {@code /api/input/**} y propaga
 * la cabecera {@code X-User-Id}.
 */
@RestController
@RequestMapping("/input")
@RequiredArgsConstructor
public class InputController {

    private final InputService inputService;
    private final I18nService i18n;

    @GetMapping
    public ResponseEntity<PageResponse<Input>> list(
            @RequestParam(required = false) InputCategory category,
            @RequestParam(required = false) String q,
            @RequestParam(name = "low_stock_only", defaultValue = "false") boolean lowStockOnly,
            @RequestParam(name = "include_deleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inputService.search(category, q, lowStockOnly, includeDeleted, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Input> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(inputService.getById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody InputRequest body,
            @RequestHeader(name = "X-User-Id") UUID userId) {
        UUID id = inputService.create(body, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id, "message", i18n.getMessage("input.created")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody InputUpdateRequest body) {
        inputService.update(id, body);
        return ResponseEntity.ok(Map.of("message", i18n.getMessage("input.updated")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        inputService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
