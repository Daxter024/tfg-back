package com.agro.terrainservice.constants;

/**
 * Sistemas de riego soportados por el servicio.
 * Los valores deben coincidir con los del tipo enum {@code irrigation_type} en PostgreSQL
 * (ver migracion V3__extend_terrain_descriptive_fields.sql).
 */
public enum IrrigationType {
    goteo,
    aspersion,
    gravedad,
    secano
}
