package com.agro.terrainservice.controller;

import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.TerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;
    private final I18nService i18nService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTerrains(
            @RequestParam(required = true) UUID user_id,
            @RequestParam(required = false) List<TerrainFields> fields
    ) {
        return ResponseEntity.ok(terrainService.getTerrains(user_id, fields));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTerrain(
            @PathVariable UUID id,
            @RequestParam(required = false) List<TerrainFields> fields
    ) {
        return ResponseEntity.ok(terrainService.getTerrain(id, fields));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody TerrainRequest dto
    ) {
        UUID id = terrainService.create(dto);
        Map<String, Object> body = Map.of(
                "id", id,
                "message", i18nService.getMessage("terrain.created", dto.name())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(required = true) UUID user_id
    ) {
        terrainService.deleteTerrain(id, user_id);
        return ResponseEntity.noContent().build();
    }
}
