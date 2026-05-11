package com.agro.taskservice.exception;

/**
 * Operación denegada por propiedad cruzada: el recurso existe pero el usuario
 * actual no es su propietario. Mapea a HTTP 403 en {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
