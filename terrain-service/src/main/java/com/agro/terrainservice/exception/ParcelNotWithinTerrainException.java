package com.agro.terrainservice.exception;

/**
 * 400 — la parcela no está geométricamente contenida en el terreno padre.
 */
public class ParcelNotWithinTerrainException extends RuntimeException {
    public ParcelNotWithinTerrainException(String message) {
        super(message);
    }
}
