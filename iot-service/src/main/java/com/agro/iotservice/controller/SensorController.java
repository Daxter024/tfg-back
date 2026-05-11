package com.agro.iotservice.controller;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.SensorRequest;
import com.agro.iotservice.dto.SensorUpdateRequest;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.service.I18nService;
import com.agro.iotservice.service.SensorService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sensor admin endpoints. Gated by the gateway JWT filter — X-User-Id is
 * the identity propagated by the JwtValidation filter (see api-gateway).
 */
@RestController
@RequestMapping("/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;
    private final I18nService i18n;

    @GetMapping
    public ResponseEntity<List<Sensor>> list(
            @RequestParam(name = "terrain_id", required = false) UUID terrainId,
            @RequestParam(required = false) VariableKind variable,
            @RequestParam(required = false) SensorStatus status) {
        return ResponseEntity.ok(sensorService.search(terrainId, variable, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sensor> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(sensorService.getById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody SensorRequest body,
            @RequestHeader(name = "X-User-Id") UUID userId) {
        UUID id = sensorService.create(body, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id, "message", i18n.getMessage("sensor.created")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SensorUpdateRequest body) {
        sensorService.update(id, body);
        return ResponseEntity.ok(Map.of("message", i18n.getMessage("sensor.updated")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sensorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
