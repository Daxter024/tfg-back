package com.agro.terrainservice.constants;

/**
 * Tipos de suelo soportados por el servicio.
 * Los valores deben coincidir con los del tipo enum {@code soil_type} en PostgreSQL
 * (ver migracion V3__extend_terrain_descriptive_fields.sql).
 */
public enum SoilType {
    arcilloso,
    franco,
    arenoso,
    calizo,
    organico,
    otro
}
