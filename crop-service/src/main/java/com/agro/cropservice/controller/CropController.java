package com.agro.cropservice.controller;

import com.agro.cropservice.dto.CropRequest;
import com.agro.cropservice.model.CropType;
import com.agro.cropservice.service.CropService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/crop")
@RequiredArgsConstructor
public class CropController {

    private final CropService cropService;

    @GetMapping
    public ResponseEntity<List<?>> getCrops(
            @RequestParam(required = false) String fields
    ) {
        var response = cropService.getCrops(fields);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/type")
    public ResponseEntity<List<CropType>> getCropTypes() {
        var response = cropService.getCropTypes();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> createCrop(
            @Valid @RequestBody CropRequest cropRequest
    ) {
        var response = cropService.createCrop(cropRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCrop(
            @PathVariable UUID id
    ) {
        var response = cropService.deleteCrop(id);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(response);
    }
}
