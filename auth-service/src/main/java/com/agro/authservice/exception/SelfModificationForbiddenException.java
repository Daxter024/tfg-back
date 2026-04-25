package com.agro.authservice.exception;

public class SelfModificationForbiddenException extends RuntimeException {
    public SelfModificationForbiddenException(String message) {
        super(message);
    }
}
