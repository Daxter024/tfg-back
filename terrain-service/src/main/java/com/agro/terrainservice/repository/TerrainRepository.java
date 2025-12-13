package com.agro.terrainservice.repository;

import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public void saveWithCalculations(String name, UUID user_id, String geoJson) {
        String sql = """
                INSERT INTO terrain (
                name, user_id, geometry
                )
                VALUES (
                    ?,
                    ?,
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)
                )
                """;

        jdbcTemplate.update(sql, name, user_id, geoJson);
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


//    @Override
//    public Object getTerrainByFields(UUID id, String selectedFields) {
//        String sql = String.format(
//                "SELECT %s FROM terrain WHERE id = %s",
//                selectedFields,
//                id
//        );
//
//        try {
//            return em.createNativeQuery(sql).getSingleResult();
//        } catch (NoResultException e) {
//            return null;
//        }
//    }
}
