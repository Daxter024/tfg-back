package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.dto.TerrainResponseDTO;
import com.agro.terrainservice.entity.Terrain;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.mapper.TerrainMapper;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TerrainService {
    private final TerrainRepository terrainRepository;
    private final I18nService i18nService;
    private final ObjectMapper mapper;
    private final TerrainMapper terrainMapper;
    private final FieldFilter fieldFilter = new FieldFilter();

    public MappingJacksonValue getTerrain(UUID id, String fields) {

        String notFoundMsg = i18nService.getMessage("terrain.notfound", id);

        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new TerrainNotFoundException(notFoundMsg));

        TerrainResponseDTO dto = terrainMapper.toDTO(terrain);

        MappingJacksonValue wrapper = new MappingJacksonValue(dto);

        SimpleFilterProvider filter = fieldFilter.filter(fields, "terrainFilter");

        wrapper.setFilters(filter);

        return wrapper;
    }

    @Transactional
    public String create(TerrainRequest dto) {
        try {
            String geoJson = mapper.writeValueAsString(dto.geometry());
            terrainRepository.saveWithCalculations(dto.name(), geoJson);
            return i18nService.getMessage("terrain.created", dto.name());
        } catch (Exception e) {
            throw new RuntimeException(i18nService.getMessage("error.geojson"), e);
        }
    }

    @Transactional
    public String delete(UUID id) {
        // TODO: En el futuro comprobar si el idUser es el mismo para uqe permita borrarlo
        boolean exists = terrainRepository.existsById(id);
        if (!exists) {
            String notFoundMessage = i18nService.getMessage("terrain.notfound", id);
            throw new TerrainNotFoundException(notFoundMessage);
        }
        terrainRepository.deleteById(id);
        return i18nService.getMessage("terrain.deleted", id);
    }
}
