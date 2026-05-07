package com.agro.terrainservice.exception;

/**
 * Lanzada cuando el cliente solicita un campo de proyeccion ({@code fields=...})
 * que no existe en el catalogo permitido. Se mapea a HTTP 400.
 */
public class InvalidFieldException extends RuntimeException {
    public InvalidFieldException(String message) {
        super(message);
    }
}
