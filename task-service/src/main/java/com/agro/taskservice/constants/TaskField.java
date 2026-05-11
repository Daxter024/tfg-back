package com.agro.taskservice.constants;

/**
 * Enum de campos proyectables por el parametro {@code fields=} en
 * {@code GET /task} y {@code GET /task/{id}}. Solo las columnas listadas aqui
 * pueden interpolarse en el SQL (proteccion contra SQLi).
 */
public enum TaskField {
    id,
    task_type_id,
    terrain_id,
    planned_at,
    estimated_duration_minutes,
    state,
    started_at,
    finished_at,
    real_duration_minutes,
    created_by,
    assigned_to,
    recurrence_parent_id,
    recurrence_rule,
    notes,
    planned_inputs,
    consumed_inputs,
    created_at,
    updated_at
}
