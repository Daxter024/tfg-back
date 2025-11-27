package com.agro.cropservice.controller;

import com.agro.cropservice.service.CropService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
