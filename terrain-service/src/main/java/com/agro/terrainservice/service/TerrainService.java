package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldsValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TerrainService {
    private final TerrainRepository terrainRepository;
    private final I18nService i18nService;
    private final ObjectMapper mapper;
    private final FieldsValidator fieldsValidator;
    private final UserGrpcClient userGrpcClient;

    @Transactional(readOnly = true)
    public Map<String, Object> getTerrain(UUID id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return terrainRepository.getTerrain(id, selectedFields);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTerrains(UUID user_id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return terrainRepository.getTerrains(user_id, selectedFields);
    }

    @Transactional
    public String create(TerrainRequest dto) {
        if (!userGrpcClient.validateUser(dto.user_id())) {
            // TODO
            // I am throwing a RuntimeException cause i dont want the client/user to know the logic behind that
            // I should write a Log Slf4j to let know what is really happening
            throw new RuntimeException(i18nService.getMessage("user.notfound", dto.user_id()));
        }
        try {
            String geoJson = mapper.writeValueAsString(dto.geometry());
            terrainRepository.saveWithCalculations(dto.name(), dto.user_id(), geoJson);
            return i18nService.getMessage("terrain.created", dto.name());
        } catch (Exception e) {
            throw new RuntimeException(i18nService.getMessage("error.geojson"), e);
        }
    }

    @Transactional
    public String delete(UUID id, UUID user_id) {
        terrainRepository.deleteTerrain(id, user_id);
        return i18nService.getMessage("terrain.deleted", id);
    }
}
