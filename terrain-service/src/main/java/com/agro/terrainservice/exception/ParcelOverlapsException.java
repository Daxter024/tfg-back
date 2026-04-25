package com.agro.terrainservice.exception;

/**
 * 409 — la parcela se solapa geométricamente con otra parcela existente
 * del mismo terreno.
 */
public class ParcelOverlapsException extends RuntimeException {
    public ParcelOverlapsException(String message) {
        super(message);
    }
}
