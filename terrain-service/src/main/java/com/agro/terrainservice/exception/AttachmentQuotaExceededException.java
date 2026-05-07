package com.agro.terrainservice.exception;

/**
 * HU-TER-03: lanzada cuando se supera la cuota acumulada de adjuntos por terreno
 * (100 MB). Se mapea a HTTP 400.
 */
public class AttachmentQuotaExceededException extends RuntimeException {
    public AttachmentQuotaExceededException(String message) {
        super(message);
    }
}
