package com.agro.taskservice.exception;

import com.agro.taskservice.service.I18nService;
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
 * Handler global — todas las respuestas de error usan {@link ProblemDetail}
 * (RFC 7807) y los mensajes se resuelven via {@link I18nService}.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nService i18nService;

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTaskNotFound(TaskNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "task.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(TerrainNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTerrainNotFound(TerrainNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "task.terrain.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "task.user.not.found.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "task.transition.invalid.title", ex.getMessage());
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ProblemDetail> handleInvalidField(InvalidFieldException ex) {
        return problem(HttpStatus.BAD_REQUEST, "task.invalid.field.title", ex.getMessage());
    }

    @ExceptionHandler(RecurrenceExceededException.class)
    public ResponseEntity<ProblemDetail> handleRecurrenceExceeded(RecurrenceExceededException ex) {
        return problem(HttpStatus.BAD_REQUEST, "task.recurrence.exceeded.title", ex.getMessage());
    }

    @ExceptionHandler(TaskDeleteConflictException.class)
    public ResponseEntity<ProblemDetail> handleDeleteConflict(TaskDeleteConflictException ex) {
        return problem(HttpStatus.CONFLICT, "task.delete.conflict.title", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "task.forbidden.title", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.toList());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle(i18nService.getMessage("task.validation.title"));
        pd.setDetail(i18nService.getMessage("task.validation.detail"));
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "task.invalid.field.title",
                i18nService.getMessage("task.invalid.argument", ex.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "task.invalid.field.title", ex.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String titleKey, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(i18nService.getMessage(titleKey));
        return ResponseEntity.status(status).body(pd);
    }
}
