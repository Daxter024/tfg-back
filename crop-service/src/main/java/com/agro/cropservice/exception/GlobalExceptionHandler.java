package com.agro.cropservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrityViolationException(IntegrityViolationException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Integrity violation");
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }
}
