package com.agro.cropservice.repository;

import com.agro.cropservice.dto.CropDetailsDTO;
import com.agro.cropservice.exception.IntegrityViolationException;
import com.agro.cropservice.model.Crop;
import com.agro.cropservice.model.CropType;
import com.agro.cropservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CropRepository {

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;

    private final RowMapper<CropType> cropTypeMapper =
            (rs, rowNum) -> new CropType(rs.getInt("id"), rs.getString("name"));

    private final RowMapper<Crop> cropMapper =
            (rs, rowNum) -> new Crop(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("crop_type_id")
            );

    private final RowMapper<CropDetailsDTO> cropDetailsMapper =
            (rs, rowNum) -> new CropDetailsDTO(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("crop_type_id"),
                    rs.getString("crop_type_name")
            );

    public int insertCrop(String name, String description, Integer crop_type_id) {
        String sql = "INSERT INTO crop (name, description, crop_type_id) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, name, description, crop_type_id);
    }

    public boolean cropTypeExists(Integer crop_type_id) {
        String sql = "SELECT COUNT(*) FROM crop_type WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, crop_type_id);
        return (count != null && count > 0);
    }

    public boolean cropExists(UUID id) {
        String sql = "SELECT COUNT(*) FROM crop WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return (count != null && count > 0);
    }

    public void insertCropType(String name) {
        String sql = "INSERT INTO crop_type (name) VALUES (?)";
        jdbcTemplate.update(sql, name);
    }

    public List<Crop> findAllCrops() {
        String sql = "SELECT * FROM crop";
        return jdbcTemplate.query(sql, cropMapper);
    }

    public List<Map<String, Object>> findAllCrops(String fields) {
        return findAllCrops(fields, null);
    }

    public List<Map<String, Object>> findAllCrops(String fields, Integer cropTypeId) {
        if (cropTypeId == null) {
            String sql = "SELECT " + fields + " FROM crop";
            return jdbcTemplate.queryForList(sql);
        }
        String sql = "SELECT " + fields + " FROM crop WHERE crop_type_id = ?";
        return jdbcTemplate.queryForList(sql, cropTypeId);
    }

    public List<CropType> findAllCropTypes() {
        String sql = "SELECT * FROM crop_type";
        return jdbcTemplate.query(sql, cropTypeMapper);
    }

    public List<CropDetailsDTO> findAllCropDetails() {
        String sql = """
                SELECT
                    c.id,
                    c.name,
                    c.description,
                    c.crop_type_id
                    ct.name AS crop_type_name
                FROM crop c
                INNER JOIN
                    croptype ct ON c.crop_type_id = ct.id
                """;
        return jdbcTemplate.query(sql, cropDetailsMapper);
    }

    public int deleteCropType(Integer cropTypeId) {
        List<String> cropNames = jdbcTemplate.queryForList(
                "SELECT name FROM crop WHERE crop_type_id = ?",
                String.class,
                cropTypeId
        );

        if (!cropNames.isEmpty()) {
            String associatedCrops = String.join(",", cropNames);
            String errorMessage = i18nService.getMessage("crop.integrity.exception", associatedCrops);
            throw new IntegrityViolationException(errorMessage);
        }

        return jdbcTemplate.update("DELETE FROM crop_type WHERE id = ?", cropTypeId);
    }

    public int deleteCrop(UUID id) {
        Boolean exists = cropExists(id);
        if (!exists) {
            throw new IllegalArgumentException("crop not found");
        }

        String sql = "DELETE FROM crop WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }
}
