package com.agro.terrainservice.repository;

import com.agro.terrainservice.exception.ParcelNotFoundException;
import com.agro.terrainservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ParcelRepository {

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;

    /** Devuelve un mapa con los campos pedidos. Lanza 404 si no existe. */
    public Map<String, Object> getParcel(UUID terrainId, UUID parcelId, String fields) {
        String sql = "SELECT " + fields + " FROM parcel WHERE id = ? AND terrain_id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, parcelId, terrainId);
        } catch (EmptyResultDataAccessException e) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.not.found", parcelId)
            );
        }
    }

    public List<Map<String, Object>> getParcelsByTerrain(UUID terrainId, String fields) {
        String sql = "SELECT " + fields + " FROM parcel WHERE terrain_id = ?";
        return jdbcTemplate.queryForList(sql, terrainId);
    }

    public List<UUID> findIdsByTerrainId(UUID terrainId) {
        String sql = "SELECT id FROM parcel WHERE terrain_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> UUID.fromString(rs.getString("id")), terrainId);
    }

    public boolean existsById(UUID id) {
        String sql = "SELECT count(*) FROM parcel WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * Valida que el GeoJSON dado quede integramente dentro del polígono del
     * terreno indicado. Devuelve {@code true} si lo esta o si el terreno no
     * existe (en cuyo caso la responsabilidad es de capas superiores).
     */
    public boolean isWithinTerrain(UUID terrainId, String geoJson) {
        String sql = """
                SELECT ST_Within(
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326),
                    geometry
                )
                FROM terrain WHERE id = ?
                """;
        Boolean within = jdbcTemplate.queryForObject(sql, Boolean.class, geoJson, terrainId);
        return Boolean.TRUE.equals(within);
    }

    /**
     * Devuelve los nombres de parcelas del mismo terreno cuya geometria se
     * solape con la nueva. {@code excludeParcelId} se usa al hacer PATCH para
     * excluir la parcela que se esta editando.
     */
    public List<String> findOverlapping(UUID terrainId, String geoJson, UUID excludeParcelId) {
        if (excludeParcelId == null) {
            String sql = """
                    SELECT name FROM parcel
                    WHERE terrain_id = ?
                      AND ST_Overlaps(geometry, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                    """;
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString("name"), terrainId, geoJson);
        }
        String sql = """
                SELECT name FROM parcel
                WHERE terrain_id = ?
                  AND id <> ?
                  AND ST_Overlaps(geometry, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("name"), terrainId, excludeParcelId, geoJson);
    }

    public boolean nameExistsInTerrain(UUID terrainId, String name, UUID excludeParcelId) {
        if (excludeParcelId == null) {
            String sql = "SELECT count(*) FROM parcel WHERE terrain_id = ? AND name = ?";
            Integer c = jdbcTemplate.queryForObject(sql, Integer.class, terrainId, name);
            return c != null && c > 0;
        }
        String sql = "SELECT count(*) FROM parcel WHERE terrain_id = ? AND name = ? AND id <> ?";
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, terrainId, name, excludeParcelId);
        return c != null && c > 0;
    }

    public UUID insert(UUID terrainId, String name, String geoJson) {
        String sql = """
                INSERT INTO parcel (terrain_id, name, geometry)
                VALUES (?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, UUID.class, terrainId, name, geoJson);
    }

    public void updateName(UUID terrainId, UUID parcelId, String newName) {
        String sql = "UPDATE parcel SET name = ? WHERE id = ? AND terrain_id = ?";
        int rows = jdbcTemplate.update(sql, newName, parcelId, terrainId);
        if (rows == 0) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.not.found", parcelId)
            );
        }
    }

    public void updateGeometry(UUID terrainId, UUID parcelId, String geoJson) {
        String sql = """
                UPDATE parcel
                SET geometry = ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)
                WHERE id = ? AND terrain_id = ?
                """;
        int rows = jdbcTemplate.update(sql, geoJson, parcelId, terrainId);
        if (rows == 0) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.not.found", parcelId)
            );
        }
    }

    public void deleteParcel(UUID terrainId, UUID parcelId) {
        String sql = "DELETE FROM parcel WHERE id = ? AND terrain_id = ?";
        int rows = jdbcTemplate.update(sql, parcelId, terrainId);
        if (rows == 0) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.not.found", parcelId)
            );
        }
    }

    /** Resumen agregado para la ficha del terreno (HU-TER-04 §3.6). */
    public Map<String, Object> getParcelsSummary(UUID terrainId) {
        String sql = """
                SELECT
                    COUNT(*) AS parcel_count,
                    COALESCE(SUM(area_m2), 0) AS parcels_total_area
                FROM parcel
                WHERE terrain_id = ?
                """;
        return jdbcTemplate.queryForMap(sql, terrainId);
    }
}
