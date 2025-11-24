package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.service.TerrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;

    @GetMapping("/{id}")
    public ResponseEntity<MappingJacksonValue> getTerrain(
            @PathVariable UUID id,
            @RequestParam(required = false) String fields
    ) {
        var response = terrainService.getTerrain(id, fields);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    @PostMapping("/create")
    public ResponseEntity<String> create(
            @RequestBody TerrainRequest dto
    ) {
        String res = terrainService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

}
