package com.agro.iotservice.constants;

/**
 * Catalog of physical variables that a sensor can record. Matches the
 * {@code variable_kind} Postgres enum in V1.
 */
public enum VariableKind {
    temperature,
    humidity,
    ph,
    soil_moisture,
    wind_speed,
    rainfall,
    luminosity,
    other
}
