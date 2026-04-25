package com.agro.authservice.exception;

import com.agro.authservice.service.I18nService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final I18nService i18nService;

    public GlobalExceptionHandler(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    String defaultMessage = error.getDefaultMessage();
                    String messageKey = defaultMessage.replaceAll("[{}]", "");
                    String resolvedMessage = i18nService.getMessage(messageKey);
                    return error.getField() + ": " + resolvedMessage;
                })
                .collect(Collectors.toList());

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(i18nService.getMessage("auth.validation.error.title"));
        problemDetail.setProperty("errors", errors);
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(EmailNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEmailNotFoundException(EmailNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.email.not.found.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.invalid.credentials.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ProblemDetail> handleJwtException(JwtException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.invalid.token.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("user.email.exists.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ProblemDetail> handlePasswordMismatchException(PasswordMismatchException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.validation.error.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountLockedException(AccountLockedException ex) {
        HttpStatus status = HttpStatus.LOCKED;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.account.locked.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ProblemDetail> handleAccountInactiveException(AccountInactiveException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.account.inactive.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.refresh.invalid.title"));
        return ResponseEntity
                .status(status)
                .body(problemDetail);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFoundException(UserNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("user.not.found.title"));
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(SelfModificationForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleSelfModificationForbiddenException(SelfModificationForbiddenException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("user.self.forbidden.title"));
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ForbiddenRoleException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenRoleException(ForbiddenRoleException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("user.admin.required.title"));
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRoleException(InvalidRoleException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(i18nService.getMessage("auth.validation.error.title"));
        return ResponseEntity.status(status).body(problemDetail);
    }
}
