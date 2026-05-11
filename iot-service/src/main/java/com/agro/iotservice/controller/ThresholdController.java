package com.agro.iotservice.controller;

import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.ThresholdRequest;
import com.agro.iotservice.dto.ThresholdUpdateRequest;
import com.agro.iotservice.model.Threshold;
import com.agro.iotservice.service.I18nService;
import com.agro.iotservice.service.ThresholdService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/threshold")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdService service;
    private final I18nService i18n;

    @GetMapping
    public ResponseEntity<List<Threshold>> list(
            @RequestParam(name = "sensor_id", required = false) UUID sensorId,
            @RequestParam(required = false) VariableKind variable) {
        return ResponseEntity.ok(service.search(sensorId, variable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Threshold> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ThresholdRequest body) {
        UUID id = service.create(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id, "message", i18n.getMessage("threshold.created")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ThresholdUpdateRequest body) {
        service.update(id, body);
        return ResponseEntity.ok(Map.of("message", i18n.getMessage("threshold.updated")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
