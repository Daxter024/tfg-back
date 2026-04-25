package com.agro.terrainservice.event;

import java.util.UUID;

/**
 * Evento publicado en el topic {@code parcel-deleted} cuando se borra una
 * parcela (explícita o en cascada por borrado de su terreno padre).
 * Lo consumirán {@code season-service}, {@code task-service} e
 * {@code iot-service} para limpiar referencias.
 */
public record ParcelDeletedEvent(UUID parcelId, UUID terrainId) {
}
