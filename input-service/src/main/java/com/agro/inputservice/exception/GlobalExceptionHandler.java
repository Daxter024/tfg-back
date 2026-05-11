package com.agro.inputservice.exception;

import com.agro.inputservice.service.I18nService;
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
 * Handler global — todas las respuestas de error son {@link ProblemDetail}
 * (RFC 7807) y los mensajes se resuelven via {@link I18nService}.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nService i18nService;

    @ExceptionHandler(InputNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleInputNotFound(InputNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "input.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(MovementNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMovementNotFound(MovementNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "input.movement.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(InputNameDuplicatedException.class)
    public ResponseEntity<ProblemDetail> handleNameDuplicated(InputNameDuplicatedException ex) {
        return problem(HttpStatus.CONFLICT, "input.name.duplicated.title", ex.getMessage());
    }

    @ExceptionHandler(CategoryImmutableException.class)
    public ResponseEntity<ProblemDetail> handleCategoryImmutable(CategoryImmutableException ex) {
        return problem(HttpStatus.CONFLICT, "input.category.immutable.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidMovementException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMovement(InvalidMovementException ex) {
        return problem(HttpStatus.BAD_REQUEST, "input.movement.invalid.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ProblemDetail> handleInvalidField(InvalidFieldException ex) {
        return problem(HttpStatus.BAD_REQUEST, "input.invalid.field.title", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.toList());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle(i18nService.getMessage("input.validation.title"));
        pd.setDetail(i18nService.getMessage("input.validation.detail"));
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "input.invalid.field.title",
                i18nService.getMessage("input.invalid.argument", ex.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "input.invalid.field.title", ex.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String titleKey, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(i18nService.getMessage(titleKey));
        return ResponseEntity.status(status).body(pd);
    }
}
