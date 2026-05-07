package com.agro.terrainservice.exception;

/**
 * HU-TER-01: lanzada cuando el area calculada queda fuera del rango admitido
 * (entre 0,01 ha y 10 000 ha). Se mapea a HTTP 400 con mensaje i18n.
 */
public class AreaOutOfRangeException extends RuntimeException {
    public AreaOutOfRangeException(String message) {
        super(message);
    }
}
