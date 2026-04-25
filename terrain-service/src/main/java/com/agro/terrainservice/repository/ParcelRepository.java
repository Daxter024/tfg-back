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

    public Map<String, Object> getParcel(UUID terrainId, UUID parcelId, String fields) {
        String sql = "SELECT " + fields + " FROM parcel WHERE id = ? AND terrain_id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, parcelId, terrainId);
        } catch (EmptyResultDataAccessException e) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.notfound", parcelId)
            );
        }
    }

    public List<Map<String, Object>> getParcels(UUID terrainId, String fields) {
        String sql = "SELECT " + fields + " FROM parcel WHERE terrain_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, terrainId);
    }

    public List<UUID> findIdsByTerrainId(UUID terrainId) {
        String sql = "SELECT id FROM parcel WHERE terrain_id = ?";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> UUID.fromString(rs.getString("id")), terrainId);
    }

    /**
     * Inserta una parcela. La validación de contención y solapamiento se hace
     * antes en el service.
     */
    public UUID save(UUID terrainId, String name, String geoJson) {
        String sql = """
                INSERT INTO parcel (terrain_id, name, geometry)
                VALUES (?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, UUID.class, terrainId, name, geoJson);
    }

    public int updateName(UUID parcelId, UUID terrainId, String name) {
        String sql = "UPDATE parcel SET name = ? WHERE id = ? AND terrain_id = ?";
        return jdbcTemplate.update(sql, name, parcelId, terrainId);
    }

    public int updateGeometry(UUID parcelId, UUID terrainId, String geoJson) {
        String sql = """
                UPDATE parcel
                   SET geometry = ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)
                 WHERE id = ? AND terrain_id = ?
                """;
        return jdbcTemplate.update(sql, geoJson, parcelId, terrainId);
    }

    public int deleteById(UUID parcelId, UUID terrainId) {
        String sql = "DELETE FROM parcel WHERE id = ? AND terrain_id = ?";
        return jdbcTemplate.update(sql, parcelId, terrainId);
    }

    public boolean existsById(UUID parcelId) {
        String sql = "SELECT count(*) FROM parcel WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parcelId);
        return count != null && count > 0;
    }

    /**
     * Comprueba que la parcela está geométricamente contenida en el terreno
     * padre. Devuelve true si {@code ST_Within(parcel.geom, terrain.geom)}.
     */
    public boolean isWithinTerrain(UUID terrainId, String geoJson) {
        String sql = """
                SELECT ST_Within(
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326),
                    (SELECT geometry FROM terrain WHERE id = ?)
                )
                """;
        Boolean within = jdbcTemplate.queryForObject(sql, Boolean.class, geoJson, terrainId);
        return Boolean.TRUE.equals(within);
    }

    /**
     * Devuelve el nombre de la primera parcela del mismo terreno que se solapa
     * con la geometría dada. {@code excludeParcelId} se ignora del check
     * (útil en UPDATE para no comparar contra sí misma); puede ser null.
     */
    public String findOverlappingParcelName(UUID terrainId, String geoJson, UUID excludeParcelId) {
        String sql = """
                SELECT name FROM parcel
                 WHERE terrain_id = ?
                   AND (CAST(? AS uuid) IS NULL OR id <> CAST(? AS uuid))
                   AND ST_Overlaps(geometry, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                 LIMIT 1
                """;
        try {
            String exclude = excludeParcelId == null ? null : excludeParcelId.toString();
            return jdbcTemplate.queryForObject(sql, String.class,
                    terrainId, exclude, exclude, geoJson);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Suma de áreas de parcelas del terreno (m²).
     */
    public double sumAreaByTerrainId(UUID terrainId) {
        String sql = "SELECT COALESCE(SUM(area_m2), 0) FROM parcel WHERE terrain_id = ?";
        Double total = jdbcTemplate.queryForObject(sql, Double.class, terrainId);
        return total == null ? 0d : total;
    }

    public int countByTerrainId(UUID terrainId) {
        String sql = "SELECT COUNT(*) FROM parcel WHERE terrain_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, terrainId);
        return count == null ? 0 : count;
    }
}
