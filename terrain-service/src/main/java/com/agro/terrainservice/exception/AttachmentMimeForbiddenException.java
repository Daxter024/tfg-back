package com.agro.terrainservice.exception;

/**
 * HU-TER-03: lanzada cuando se intenta subir un adjunto con un MIME type fuera
 * de la whitelist (jpeg/png/pdf). Se mapea a HTTP 415.
 */
public class AttachmentMimeForbiddenException extends RuntimeException {
    public AttachmentMimeForbiddenException(String message) {
        super(message);
    }
}
