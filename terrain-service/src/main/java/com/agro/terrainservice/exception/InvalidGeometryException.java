package com.agro.terrainservice.exception;

/**
 * Lanzada cuando la geometría enviada por el cliente no es un polígono válido
 * (auto-intersección, menos de 4 vértices, anillo no cerrado, etc.).
 */
public class InvalidGeometryException extends RuntimeException {
    public InvalidGeometryException(String message) {
        super(message);
    }
}
