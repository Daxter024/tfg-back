package com.agro.taskservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notificacion almacenada en la bandeja del user. {@code task_id} es
 * nullable porque hay fuentes que no nacen de una task (stock-low,
 * sensor-alert).
 */
public record Notification(
        UUID id,
        UUID user_id,
        UUID task_id,
        String source_kind,
        UUID source_ref,
        String channel,
        String title,
        String body,
        LocalDateTime created_at,
        LocalDateTime read_at
) {
}
