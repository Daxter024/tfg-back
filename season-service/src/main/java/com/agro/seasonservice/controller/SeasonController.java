package com.agro.seasonservice.controller;

import com.agro.seasonservice.dto.SeasonRequest;
import com.agro.seasonservice.service.SeasonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/season")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getSeason(
            @PathVariable UUID id,
            @RequestParam(required = false) String fields
    ) {
        var response = seasonService.getSeason(id, fields);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/terrain/{terrainId}")
    public ResponseEntity<?> getSeasonsByTerrain(
            @PathVariable UUID terrainId,
            @RequestParam(required = false) String fields
    ) {
        var response = seasonService.getSeasonsByTerrain(terrainId, fields);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createSeason(
            @Valid @RequestBody SeasonRequest request
    ) {
        var response = seasonService.createSeason(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSeason(
            @PathVariable UUID id
    ) {
        seasonService.deleteSeason(id);
        return ResponseEntity.noContent().build();
    }
}
