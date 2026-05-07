package com.agro.terrainservice.exception;

/**
 * HU-TER-04: lanzada cuando una parcela se solapa con otra ya existente del
 * mismo terreno (por {@code ST_Overlaps}). Se mapea a HTTP 409.
 */
public class ParcelOverlapException extends RuntimeException {
    public ParcelOverlapException(String message) {
        super(message);
    }
}
