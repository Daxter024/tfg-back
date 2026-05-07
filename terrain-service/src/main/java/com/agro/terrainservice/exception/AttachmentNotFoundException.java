package com.agro.terrainservice.exception;

/**
 * HU-TER-03: lanzada cuando un adjunto referenciado no existe. Se mapea a 404.
 */
public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(String message) {
        super(message);
    }
}
