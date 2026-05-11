package com.agro.taskservice.model;

/**
 * Catalogo de tipos de tarea. El {@code label_key} apunta a una clave i18n
 * en {@code i18n/messages*.properties}.
 */
public record TaskType(
        Integer id,
        String code,
        String label_key
) {
}
