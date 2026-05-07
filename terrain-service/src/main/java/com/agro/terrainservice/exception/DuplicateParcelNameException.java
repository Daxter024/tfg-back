package com.agro.terrainservice.exception;

/**
 * HU-TER-04: lanzada cuando una parcela del mismo terreno ya tiene el mismo
 * nombre (constraint UNIQUE (terrain_id, name)). Se mapea a HTTP 409.
 */
public class DuplicateParcelNameException extends RuntimeException {
    public DuplicateParcelNameException(String message) {
        super(message);
    }
}
