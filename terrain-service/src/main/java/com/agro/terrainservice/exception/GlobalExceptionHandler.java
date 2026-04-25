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

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAttachmentNotFoundException(AttachmentNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Attachment not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaTypeException(UnsupportedMediaTypeException ex) {
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Unsupported media type");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ProblemDetail> handlePayloadTooLargeException(PayloadTooLargeException ex) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Payload too large");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(AttachmentQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleAttachmentQuotaExceededException(AttachmentQuotaExceededException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Attachment quota exceeded");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                i18nService.getMessage("attachment.size.exceeded"));
        problemDetail.setTitle("Payload too large");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleParcelNotFoundException(ParcelNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelNotWithinTerrainException.class)
    public ResponseEntity<ProblemDetail> handleParcelNotWithinTerrainException(ParcelNotWithinTerrainException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel not within terrain");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelOverlapsException.class)
    public ResponseEntity<ProblemDetail> handleParcelOverlapsException(ParcelOverlapsException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel overlaps existing parcel");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(CadastralReferenceMalformedException.class)
    public ResponseEntity<ProblemDetail> handleCadastralReferenceMalformedException(
            CadastralReferenceMalformedException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Malformed cadastral reference");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(CadastralReferenceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCadastralReferenceNotFoundException(
            CadastralReferenceNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Cadastral reference not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(CadastralApiTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleCadastralApiTimeoutException(
            CadastralApiTimeoutException ex) {
        HttpStatus status = HttpStatus.GATEWAY_TIMEOUT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                i18nService.getMessage("cadastral.api.timeout"));
        problemDetail.setTitle("Cadastral API timeout");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(CadastralApiUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleCadastralApiUnavailableException(
            CadastralApiUnavailableException ex) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                i18nService.getMessage("cadastral.api.unavailable"));
        problemDetail.setTitle("Cadastral API unavailable");
        return ResponseEntity.status(status).body(problemDetail);
    }
}
