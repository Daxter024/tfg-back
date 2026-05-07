package com.agro.terrainservice.exception;

import com.agro.terrainservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

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

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFoundException(UserNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("User not found");
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
        problemDetail.setTitle("Area out of range");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFieldException(InvalidFieldException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Invalid field");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleParcelNotFoundException(ParcelNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelOverlapException.class)
    public ResponseEntity<ProblemDetail> handleParcelOverlapException(ParcelOverlapException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel overlaps with another parcel");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ParcelNotWithinTerrainException.class)
    public ResponseEntity<ProblemDetail> handleParcelNotWithinTerrainException(ParcelNotWithinTerrainException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel not within terrain");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(DuplicateParcelNameException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateParcelNameException(DuplicateParcelNameException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Parcel name already in use");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAttachmentNotFoundException(AttachmentNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Attachment not found");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(AttachmentMimeForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleAttachmentMimeForbiddenException(AttachmentMimeForbiddenException ex) {
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Attachment MIME type not allowed");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(AttachmentQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleAttachmentQuotaExceededException(AttachmentQuotaExceededException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Attachment quota exceeded");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                i18nService.getMessage("attachment.size.exceeded")
        );
        problemDetail.setTitle("Attachment size exceeded");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipartException(MultipartException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Multipart request error");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(CadastralImportException.class)
    public ResponseEntity<ProblemDetail> handleCadastralImportException(CadastralImportException ex) {
        HttpStatus status = ex.getStatus();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("Cadastral import failed");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        // Constraints CHECK de PostgreSQL (area, slope) llegan aqui si no se han
        // validado antes en service. Devolvemos 400 con un mensaje i18n generico.
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                i18nService.getMessage("terrain.integrity.violation")
        );
        problemDetail.setTitle("Data integrity violation");
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
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
        problemDetail.setTitle("Illegal argument");
        return ResponseEntity.status(status).body(problemDetail);
    }
}
