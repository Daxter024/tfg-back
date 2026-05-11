package com.agro.inputservice.model;

/**
 * Categorias del insumo (mapeo 1:1 con el enum {@code input_category} de
 * Postgres en {@code V1__create_input_and_movement.sql}).
 */
public enum InputCategory {
    fertilizante,
    fitosanitario,
    semilla,
    agua,
    combustible,
    otro
}
