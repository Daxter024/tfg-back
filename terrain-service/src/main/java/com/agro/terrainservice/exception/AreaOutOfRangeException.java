package com.agro.terrainservice.exception;

/**
 * Lanzada cuando el área del terreno (calculada a partir de la geometría)
 * cae fuera del rango operacional permitido (100 m² – 100 000 000 m²).
 */
public class AreaOutOfRangeException extends RuntimeException {
    public AreaOutOfRangeException(String message) {
        super(message);
    }
}
