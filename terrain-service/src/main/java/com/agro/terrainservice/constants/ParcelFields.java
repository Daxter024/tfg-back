package com.agro.terrainservice.constants;

/**
 * Catalogo de campos proyectables (HU-TER-04) para {@code parcel}. Igual que
 * con {@link TerrainFields}, los campos se interpolan en el SELECT y se
 * blanquean por enum, evitando inyeccion SQL.
 */
public enum ParcelFields {
    id,
    terrain_id,
    name,
    geometry,
    area_m2,
    perimeter_m,
    centroid,
    created_at,
    updated_at;
}
