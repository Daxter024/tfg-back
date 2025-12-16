package com.agro.seasonservice.service;

import com.agro.seasonservice.constants.SeasonField;
import com.agro.seasonservice.dto.SeasonRequest;
import com.agro.seasonservice.repository.SeasonRepository;
import com.agro.seasonservice.utils.FieldsValidator;
import com.agro.seasonservice.grpc.TerrainGrpcClient;
import com.agro.seasonservice.grpc.CropGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final FieldsValidator fieldsValidator;
    private final TerrainGrpcClient terrainGrpcClient;
    private final CropGrpcClient cropGrpcClient;

    @Transactional(readOnly = true)
    public Object getSeason(UUID id, List<SeasonField> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return seasonRepository.getSeason(id, selectedFields);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSeasonsByTerrain(UUID terrainId, List<SeasonField> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return seasonRepository.getSeasonsByTerrain(terrainId, selectedFields);
    }

    @Transactional
    public UUID createSeason(SeasonRequest request) {
        if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
            throw new IllegalArgumentException("Terrain with id " + request.terrain_id() + " does not exist");
        }
        if (!cropGrpcClient.checkCropExists(request.crop_id())) {
            throw new IllegalArgumentException("Crop with id " + request.crop_id() + " does not exist");
        }
        return seasonRepository.createSeason(request);
    }

    @Transactional
    public void deleteSeason(UUID id) {
        seasonRepository.deleteSeason(id);
    }
}
