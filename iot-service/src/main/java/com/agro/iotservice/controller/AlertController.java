package com.agro.iotservice.controller;

import com.agro.iotservice.constants.AlertState;
import com.agro.iotservice.dto.AlertReviewRequest;
import com.agro.iotservice.model.SensorAlert;
import com.agro.iotservice.service.AlertService;
import com.agro.iotservice.service.I18nService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;
    private final I18nService i18n;

    @GetMapping
    public ResponseEntity<List<SensorAlert>> list(
            @RequestParam(required = false) AlertState state,
            @RequestParam(name = "terrain_id", required = false) UUID terrainId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(service.search(state, terrainId, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorAlert> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AlertReviewRequest body,
            @RequestHeader(name = "X-User-Id") UUID reviewer) {
        String comment = body == null ? null : body.comment();
        service.review(id, reviewer, comment);
        return ResponseEntity.ok(Map.of("message", i18n.getMessage("alert.reviewed")));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable UUID id) {
        service.resolve(id);
        return ResponseEntity.ok(Map.of("message", i18n.getMessage("alert.resolved")));
    }
}
