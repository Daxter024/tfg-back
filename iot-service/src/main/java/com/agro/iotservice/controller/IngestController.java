package com.agro.iotservice.controller;

import com.agro.iotservice.dto.ReadingBatchRequest;
import com.agro.iotservice.ingestor.ReadingIngestor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Device-facing ingest endpoint. The X-Device-Key authentication is performed
 * by {@link com.agro.iotservice.security.DeviceKeyAuthFilter}; by the time the
 * request reaches this controller the device is trusted for {@code sensorId}.
 *
 * <p>Gateway routes this without JwtValidation — see plan §13 (excepcion
 * documentada).</p>
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final ReadingIngestor ingestor;

    @PostMapping("/sensor/{id}/reading")
    public ResponseEntity<Map<String, Object>> ingest(
            @PathVariable("id") UUID sensorId,
            @Valid @RequestBody ReadingBatchRequest body) {
        int inserted = ingestor.ingest(sensorId, body.readings());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("inserted", inserted));
    }
}
