package com.agro.seasonservice.repository;

import com.agro.seasonservice.dto.SeasonRequest;
import com.agro.seasonservice.exception.ResourceNotFoundException;
import com.agro.seasonservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SeasonRepository {

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;

    public Map<String, Object> getSeason(UUID id, String fields) {
        String sql = "SELECT " + fields + " FROM season WHERE id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException(i18nService.getMessage("season.not.found"));
        }
    }

    public List<Map<String, Object>> getSeasonsByTerrain(UUID terrainId, String fields) {
        String sql = "SELECT " + fields + " FROM season WHERE terrain_id = ? ORDER BY start_date DESC";
        return jdbcTemplate.queryForList(sql, terrainId);
    }

    public UUID createSeason(SeasonRequest request) {
        String sql = """
                    INSERT INTO season (terrain_id, crop_id, start_date, end_date, season_type_id, observations)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                UUID.class,
                request.terrain_id(),
                request.crop_id(),
                request.start_date(),
                request.end_date(),
                request.season_type_id(),
                request.observations()
        );
    }

    public void deleteSeason(UUID id) {
        String sql = "DELETE FROM season WHERE id = ?";

        int rows = jdbcTemplate.update(sql, id);

        if (rows == 0) {
            throw new ResourceNotFoundException(i18nService.getMessage("season.not.found"));
        }
    }

    public void deleteByTerrainId(UUID terrainId) {
        String sql = "DELETE FROM season WHERE terrain_id = ?";
        jdbcTemplate.update(sql, terrainId);
    }
}
