package com.agro.iotservice.exception;

import com.agro.iotservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 7807 ProblemDetail responses for every error path. Titles and details
 * resolve through {@link I18nService} — no hard-coded English strings reach
 * the client.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nService i18n;

    @ExceptionHandler(SensorNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSensorNotFound(SensorNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "iot.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(ThresholdNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleThresholdNotFound(ThresholdNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "iot.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAlertNotFound(AlertNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "iot.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(TerrainNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTerrainNotFound(TerrainNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "iot.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(DeviceKeyInvalidException.class)
    public ResponseEntity<ProblemDetail> handleDeviceKey(DeviceKeyInvalidException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "iot.unauthorized.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidThresholdException.class)
    public ResponseEntity<ProblemDetail> handleInvalidThreshold(InvalidThresholdException ex) {
        return problem(HttpStatus.BAD_REQUEST, "iot.bad-request.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidReadingException.class)
    public ResponseEntity<ProblemDetail> handleInvalidReading(InvalidReadingException ex) {
        return problem(HttpStatus.BAD_REQUEST, "iot.bad-request.title", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.toList());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle(i18n.getMessage("iot.validation.title"));
        pd.setDetail(i18n.getMessage("iot.validation.detail"));
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "iot.invalid.field.title",
                i18n.getMessage("iot.invalid.argument", ex.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "iot.bad-request.title", ex.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String titleKey, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(i18n.getMessage(titleKey));
        return ResponseEntity.status(status).body(pd);
    }
}
