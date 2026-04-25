package com.agro.terrainservice.controller;

import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.service.CadastralImportService;
import com.agro.terrainservice.service.TerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;
    private final CadastralImportService cadastralImportService;

    @GetMapping
    public ResponseEntity<?> getTerrains(
            @RequestParam(required = true) UUID user_id,
            @RequestParam(required = false) List<TerrainFields> fields
    ) {
        List<?> response = terrainService.getTerrains(user_id, fields);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTerrain(
            @PathVariable UUID id,
            @RequestParam(required = false) List<TerrainFields> fields
    ) {
        var response = terrainService.getTerrain(id, fields);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> create(
            @Valid @RequestBody TerrainRequest dto
    ) {
        String res = terrainService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * HU-TER-05 — propone un terreno a partir de una referencia catastral o
     * SIGPAC. No persiste; el cliente edita la propuesta y luego llama a
     * {@code POST /terrain} con el body resultante (incluyendo la
     * {@code cadastral_ref} para trazabilidad).
     */
    @PostMapping("/import")
    public ResponseEntity<CadastralImportResponse> importFromCadastral(
            @Valid @RequestBody CadastralImportRequest dto
    ) {
        return ResponseEntity.ok(cadastralImportService.importReference(dto));
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
