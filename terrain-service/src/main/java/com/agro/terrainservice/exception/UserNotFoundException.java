package com.agro.terrainservice.exception;

/**
 * Lanzada cuando un {@code user_id} entrante no existe en {@code auth-service}
 * (validacion gRPC previa al INSERT). Se mapea a HTTP 404.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
