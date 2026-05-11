package com.agro.taskservice.model;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Preferencias de notificacion de un user.
 *
 * <p>{@code task_type_lead_minutes_json} es la representacion JSON cruda del
 * JSONB en BBDD; el servicio lo parsea para resolver el lead time por tipo
 * de tarea ({@code {"TREATMENT": 2880, "IRRIGATION": 120}}).</p>
 */
public record NotificationPreference(
        UUID user_id,
        boolean email_enabled,
        boolean in_app_enabled,
        int default_lead_minutes,
        String task_type_lead_minutes_json,
        LocalTime quiet_hours_start,
        LocalTime quiet_hours_end,
        boolean also_notify_creator
) {
}
