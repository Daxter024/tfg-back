package com.agro.taskservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Record de dominio para {@code task}. Mantiene snake_case en los nombres de
 * componente para alinearse con las columnas SQL (patron usado en
 * season-service / crop-service para records + JdbcTemplate).
 *
 * <p>Los campos {@code planned_inputs} y {@code consumed_inputs} se serializan
 * como JSONB en la base de datos; en este record se exponen como String JSON
 * crudo para que el repositorio decida si parsear o no segun la proyeccion.</p>
 */
public record Task(
        UUID id,
        Integer task_type_id,
        UUID terrain_id,
        LocalDateTime planned_at,
        Integer estimated_duration_minutes,
        String state,
        LocalDateTime started_at,
        LocalDateTime finished_at,
        Integer real_duration_minutes,
        UUID created_by,
        UUID assigned_to,
        UUID recurrence_parent_id,
        String recurrence_rule,
        String notes,
        String planned_inputs,
        String consumed_inputs,
        LocalDateTime created_at,
        LocalDateTime updated_at
) {
}
