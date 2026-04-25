package com.agro.terrainservice.exception;

/**
 * 400 — la suma acumulada de tamaño de adjuntos del terreno superaría
 * el cupo total de 100 MB.
 */
public class AttachmentQuotaExceededException extends RuntimeException {
    public AttachmentQuotaExceededException(String message) {
        super(message);
    }
}
