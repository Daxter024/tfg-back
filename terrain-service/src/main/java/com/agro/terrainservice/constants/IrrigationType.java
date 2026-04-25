package com.agro.terrainservice.constants;

/**
 * Enum alineado con el tipo PostgreSQL `irrigation_type` (V3).
 * Los valores deben coincidir literalmente con los del ENUM de la BBDD.
 */
public enum IrrigationType {
    goteo,
    aspersion,
    gravedad,
    secano
}
