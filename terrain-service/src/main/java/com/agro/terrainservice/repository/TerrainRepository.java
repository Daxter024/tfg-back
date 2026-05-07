package com.agro.terrainservice.repository;

import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import com.agro.terrainservice.exception.TerrainNotFoundException;
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
public class TerrainRepository {

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;

    public Map<String, Object> getTerrain(UUID id, String fields) {

        String sql = "SELECT " + fields + " FROM terrain WHERE id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, id);
        } catch (EmptyResultDataAccessException e) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", id)
            );
        }
    }

    public List<Map<String, Object>> getTerrains(UUID user_id, String fields) {
        String sql = "SELECT " + fields + " FROM terrain WHERE user_id = ?";
        return jdbcTemplate.queryForList(sql, user_id);
    }

    /**
     * HU-TER-01: insert con campos descriptivos opcionales.
     * Los enums se mapean a sus tipos PostgreSQL con casts {@code ?::soil_type} /
     * {@code ?::irrigation_type}. Los nulls se aceptan en BBDD.
     */
    public UUID saveWithCalculations(
            String name,
            UUID user_id,
            String geoJson,
            SoilType soilType,
            Double slopePercent,
            IrrigationType irrigation,
            String cadastralRef
    ) {
        String sql = """
                INSERT INTO terrain (
                    name, user_id, geometry,
                    soil_type, slope_percent, irrigation, cadastral_ref
                )
                VALUES (
                    ?,
                    ?,
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326),
                    ?::soil_type,
                    ?,
                    ?::irrigation_type,
                    ?
                )
                RETURNING id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                UUID.class,
                name,
                user_id,
                geoJson,
                soilType == null ? null : soilType.name(),
                slopePercent,
                irrigation == null ? null : irrigation.name(),
                cadastralRef
        );
    }

    public void deleteTerrain(UUID id, UUID user_id) {
        String sql = "DELETE FROM terrain WHERE id = ? AND user_id = ?";
        int rows = jdbcTemplate.update(sql, id, user_id);
        if (rows == 0) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", id)
            );
        }
    }

    public boolean existsById(UUID id) {
        String sql = "SELECT count(*) FROM terrain WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public List<UUID> findIdsByUserId(UUID userId) {
        String sql = "SELECT id FROM terrain WHERE user_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> UUID.fromString(rs.getString("id")), userId);
    }

    public void deleteById(UUID id) {
        String sql = "DELETE FROM terrain WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
