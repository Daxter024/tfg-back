package com.agro.terrainservice.event;

import java.util.UUID;

/**
 * HU-TER-04: evento publicado en el topic {@code parcel-deleted} cuando se
 * borra una parcela (manualmente o en cascada porque su terreno padre o el
 * usuario propietario se borraron).
 *
 * <p>Lo consumiran {@code season-service}, {@code task-service} y
 * {@code iot-service} para limpiar sus referencias a {@code parcel_id}.</p>
 */
public record ParcelDeletedEvent(UUID parcelId, UUID terrainId) {
}
