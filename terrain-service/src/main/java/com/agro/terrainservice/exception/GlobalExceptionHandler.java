package com.agro.terrainservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TerrainNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTerrainNotFoundException(TerrainNotFoundException ex) {

        // Se usa el ProblemDetail pq sigue el RFC 7807
        
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Terrain not found");

        // problemDetail.setProperty("timestamp", Instant.now());
        // Irrelevante puesto que en la cabecera ya se incluye el timestamp

        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }
}
