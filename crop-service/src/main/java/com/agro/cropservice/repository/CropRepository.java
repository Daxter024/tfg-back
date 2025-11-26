package com.agro.cropservice.repository;

import com.agro.cropservice.dto.CropDetailsDTO;
import com.agro.cropservice.exception.IntegrityViolationException;
import com.agro.cropservice.model.Crop;
import com.agro.cropservice.model.CropType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CropRepository {

    private final JdbcTemplate jdbcTemplate;

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

    public void insertCrop(String name, String description, Integer crop_type_id) {
        String sql = "INSERT INTO crop (name, description, crop_type_id) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, name, description, crop_type_id);
    }

    public void insertCropType(String name) {
        String sql = "INSERT INTO crop_type (name) VALUES (?)";
        jdbcTemplate.update(sql, name);
    }

    public List<Crop> findAllCrops() {
        String sql = "SELECT * FROM crop";
        return jdbcTemplate.query(sql, cropMapper);
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

    public void deleteCropType(Integer cropTypeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crop WHERE crop_type_id = ?",
                Integer.class,
                cropTypeId
        );

        if (count > 0) {
            throw new IntegrityViolationException("No se puede eliminar el tipo de cultivo. Existen cultivos asociados.");
        }

        jdbcTemplate.update("DELETE FROM crop_type WHERE id = ?", cropTypeId);
    }

    public void deleteCrop(UUID id) {
        // Tener en cuenta si está en un terreno o temporada
        // Al fin y al cabo el orden de entidades que mandan son:
        // 1. User
        // 2. Terrain
        // 3. Temporada / Tarea
        // 4. Cultivo -> (hacer cultivo como una entidad ya existente que no puede ser modificada por el usuario)
    }
}
