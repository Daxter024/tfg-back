package com.agro.inputservice.constants;

/**
 * Conjunto de columnas validas para la proyeccion {@code fields=...} sobre el
 * recurso input. Cualquier valor fuera de este enum hace que
 * {@link com.agro.inputservice.utils.FieldsValidator} lance
 * InvalidFieldException — protege contra inyeccion SQL.
 */
public enum InputField {
    id,
    name,
    category,
    unit,
    low_stock_threshold,
    supplier,
    notes,
    created_by,
    created_at,
    updated_at,
    deleted_at,
    current_stock
}
