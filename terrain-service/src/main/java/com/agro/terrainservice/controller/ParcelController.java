package com.agro.terrainservice.controller;

import com.agro.terrainservice.constants.ParcelFields;
import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.service.ParcelService;
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
@RequestMapping("/terrain/{terrainId}/parcel")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @PathVariable UUID terrainId,
            @RequestParam(required = false) List<ParcelFields> fields
    ) {
        return ResponseEntity.ok(parcelService.getParcels(terrainId, fields));
    }

    @GetMapping("/{parcelId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID terrainId,
            @PathVariable UUID parcelId,
            @RequestParam(required = false) List<ParcelFields> fields
    ) {
        return ResponseEntity.ok(parcelService.getParcel(terrainId, parcelId, fields));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID terrainId,
            @Valid @RequestBody ParcelRequest dto
    ) {
        UUID id = parcelService.create(terrainId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    /**
     * PATCH (partial update) — el spec del paquete pide PATCH explícitamente
     * porque tanto `name` como `geometry` son opcionales y la semántica es de
     * actualización parcial, no de reemplazo completo.
     */
    @PatchMapping("/{parcelId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID terrainId,
            @PathVariable UUID parcelId,
            @Valid @RequestBody ParcelUpdateRequest dto
    ) {
        parcelService.update(terrainId, parcelId, dto);
        return ResponseEntity.ok().build();
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
