package com.agro.seasonservice.repository;

import com.agro.seasonservice.dto.SeasonRequest;
import com.agro.seasonservice.exception.ResourceNotFoundException;
import com.agro.seasonservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
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

    public Map<String, Object> createSeason(SeasonRequest request) {
        String sql = """
                    INSERT INTO season (terrain_id, crop_id, start_date)
                    VALUES (?, ?, ?)
                """;
        try {
            return jdbcTemplate.queryForMap(
                    sql,
                    request.terrain_id(),
                    request.crop_id(),
                    request.start_date()
            );
        } catch (DataAccessException e) {
            return null;
//            throw new DatabaseException(i18nService.getMessage("season.error.insert"));
        }
    }

    public void deleteSeason(UUID id) {
        String sql = "DELETE FROM season WHERE id = ?";

        int rows = jdbcTemplate.update(sql, id);

        if (rows == 0) {
            throw new ResourceNotFoundException(i18nService.getMessage("season.not.found"));
        }
    }
}
