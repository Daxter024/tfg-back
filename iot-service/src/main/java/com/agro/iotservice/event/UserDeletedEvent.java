package com.agro.iotservice.event;

import java.util.UUID;

/**
 * Consumed from the {@code user-deleted} topic produced by auth-service. The
 * Kafka type-mapping translates {@code com.agro.authservice.event.UserDeletedEvent}
 * to this local type.
 */
public record UserDeletedEvent(UUID userId) {
}
