package com.agro.terrainservice.exception;

/**
 * HU-TER-04: lanzada cuando la geometria de una parcela no esta integramente
 * contenida dentro del poligono del terreno padre. Se mapea a HTTP 400.
 */
public class ParcelNotWithinTerrainException extends RuntimeException {
    public ParcelNotWithinTerrainException(String message) {
        super(message);
    }
}
