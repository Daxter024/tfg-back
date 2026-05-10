package com.agro.cropservice.controller;

import com.agro.cropservice.dto.CropCreatedResponse;
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
            @RequestParam(required = false) String fields,
            @RequestParam(required = false) Integer crop_type_id
    ) {
        var response = cropService.getCrops(fields, crop_type_id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/type")
    public ResponseEntity<List<CropType>> getCropTypes() {
        var response = cropService.getCropTypes();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CropCreatedResponse> createCrop(
            @Valid @RequestBody CropRequest cropRequest
    ) {
        CropCreatedResponse response = cropService.createCrop(cropRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrop(
            @PathVariable UUID id
    ) {
        cropService.deleteCrop(id);
        return ResponseEntity.noContent().build();
    }
}
