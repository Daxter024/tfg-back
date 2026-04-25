package com.agro.terrainservice.exception;

import com.agro.terrainservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nService i18nService;

    @ExceptionHandler(TerrainNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTerrainNotFoundException(TerrainNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Terrain not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(InvalidGeometryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidGeometryException(InvalidGeometryException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Invalid geometry");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(AreaOutOfRangeException.class)
    public ResponseEntity<ProblemDetail> handleAreaOutOfRangeException(AreaOutOfRangeException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Terrain area out of range");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    String resolvedMessage = error.getDefaultMessage();
                    return error.getField() + ": " + resolvedMessage;
                })
                .collect(Collectors.toList());

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle("Wrong payload");
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Invalid argument");
        return ResponseEntity.status(status).body(problemDetail);
    }
}
