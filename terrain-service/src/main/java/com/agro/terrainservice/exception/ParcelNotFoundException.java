package com.agro.terrainservice.exception;

/**
 * HU-TER-04: lanzada cuando no se encuentra una parcela por id (o no pertenece
 * al terreno padre indicado). Se mapea a HTTP 404.
 */
public class ParcelNotFoundException extends RuntimeException {
    public ParcelNotFoundException(String message) {
        super(message);
    }
}
