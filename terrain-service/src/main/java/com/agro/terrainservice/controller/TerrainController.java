package com.agro.terrainservice.controller;

import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.service.TerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;

    // TODO: in the future implement Page<>
    @GetMapping
    public ResponseEntity<?> getTerrains(
            // de momento no quiero devolver todos los terrenos asi que el required actua como filtro para devolver solo los del user_id
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(required = true) UUID user_id
    ) {
        terrainService.deleteTerrain(id, user_id);
        return ResponseEntity.noContent().build();
    }

}
