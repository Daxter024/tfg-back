package com.agro.terrainservice.exception;

public class TerrainNotFoundException extends RuntimeException {
    public TerrainNotFoundException(String message) {
        super(message);
    }
}
