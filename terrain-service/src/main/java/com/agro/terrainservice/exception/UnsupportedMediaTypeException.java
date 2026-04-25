package com.agro.terrainservice.exception;

/**
 * 415 — el tipo MIME del archivo subido no está en la whitelist de adjuntos
 * (ver {@code AttachmentService#ALLOWED_MIME_TYPES}).
 */
public class UnsupportedMediaTypeException extends RuntimeException {
    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
