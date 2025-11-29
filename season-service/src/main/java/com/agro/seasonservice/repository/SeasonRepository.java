package com.agro.seasonservice.repository;

import com.agro.seasonservice.exception.ResourceNotFoundException;
import com.agro.seasonservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
