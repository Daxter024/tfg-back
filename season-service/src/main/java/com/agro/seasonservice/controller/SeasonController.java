package com.agro.seasonservice.controller;

import com.agro.seasonservice.service.SeasonService;
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

}
