package com.agro.terrainservice.constants;

/**
 * Enum alineado con el tipo PostgreSQL `soil_type` (V3).
 * Los valores deben coincidir literalmente con los del ENUM de la BBDD.
 */
public enum SoilType {
    arcilloso,
    franco,
    arenoso,
    calizo,
    organico,
    otro
}
