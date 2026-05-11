package com.agro.iotservice.event;

import java.util.UUID;

/**
 * Consumed from the {@code terrain-deleted} topic produced by terrain-service.
 * The Kafka type-mapping in application.properties translates
 * {@code com.agro.terrainservice.event.TerrainDeletedEvent} to this local
 * type so the JSON deserializer can wire the payload back.
 */
public record TerrainDeletedEvent(UUID terrainId) {
}
