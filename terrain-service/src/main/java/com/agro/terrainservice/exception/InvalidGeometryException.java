package com.agro.terrainservice.exception;

/**
 * Lanzada cuando un GeoJSON no se puede interpretar como un poligono valido
 * (HU-TER-01, HU-TER-04). Se mapea a HTTP 400 con un mensaje i18n.
 */
public class InvalidGeometryException extends RuntimeException {
    public InvalidGeometryException(String message) {
        super(message);
    }

    public InvalidGeometryException(String message, Throwable cause) {
        super(message, cause);
    }
}
