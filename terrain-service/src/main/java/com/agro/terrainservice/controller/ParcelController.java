package com.agro.terrainservice.controller;

import com.agro.terrainservice.constants.ParcelFields;
import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.ParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-TER-04: endpoints CRUD de parcelas anidadas bajo {@code /terrain/{id}/parcel}.
 */
@RestController
@RequestMapping("/terrain/{terrainId}/parcel")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;
    private final I18nService i18nService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @PathVariable UUID terrainId,
            @RequestParam(required = false) List<ParcelFields> fields
    ) {
        return ResponseEntity.ok(parcelService.list(terrainId, fields));
    }

    @GetMapping("/{parcelId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID terrainId,
            @PathVariable UUID parcelId,
            @RequestParam(required = false) List<ParcelFields> fields
    ) {
        return ResponseEntity.ok(parcelService.get(terrainId, parcelId, fields));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID terrainId,
            @Valid @RequestBody ParcelRequest dto
    ) {
        UUID id = parcelService.create(terrainId, dto);
        Map<String, Object> body = Map.of(
                "id", id,
                "message", i18nService.getMessage("parcel.created", dto.name())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{parcelId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID terrainId,
            @PathVariable UUID parcelId,
            @Valid @RequestBody ParcelUpdateRequest dto
    ) {
        parcelService.update(terrainId, parcelId, dto);
        Map<String, Object> body = Map.of(
                "id", parcelId,
                "message", i18nService.getMessage("parcel.updated",
                        dto.name() == null ? parcelId.toString() : dto.name())
        );
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{parcelId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID terrainId,
            @PathVariable UUID parcelId
    ) {
        parcelService.delete(terrainId, parcelId);
        return ResponseEntity.noContent().build();
    }
}
