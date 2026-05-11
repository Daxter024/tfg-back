package com.agro.taskservice.event;

import java.util.UUID;

/**
 * Evento {@code user-deleted} producido por auth-service. La tabla type-mapping
 * en {@code application.properties} traduce
 * {@code com.agro.authservice.event.UserDeletedEvent} a este tipo local.
 */
public record UserDeletedEvent(UUID userId) {
}
