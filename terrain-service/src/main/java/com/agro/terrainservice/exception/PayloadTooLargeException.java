package com.agro.terrainservice.exception;

/**
 * 413 — el archivo excede el tamaño máximo individual permitido (10 MB).
 */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
