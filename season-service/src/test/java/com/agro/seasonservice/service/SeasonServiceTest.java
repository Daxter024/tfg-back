package com.agro.seasonservice.service;

import com.agro.seasonservice.constants.SeasonField;
import com.agro.seasonservice.dto.SeasonRequest;
import com.agro.seasonservice.exception.CropNotFoundException;
import com.agro.seasonservice.exception.TerrainNotFoundException;
import com.agro.seasonservice.grpc.CropGrpcClient;
import com.agro.seasonservice.grpc.TerrainGrpcClient;
import com.agro.seasonservice.repository.SeasonRepository;
import com.agro.seasonservice.utils.FieldsValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private FieldsValidator fieldsValidator;

    @Mock
    private TerrainGrpcClient terrainGrpcClient;

    @Mock
    private CropGrpcClient cropGrpcClient;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private SeasonService seasonService;

    private SeasonRequest validRequest() {
        return new SeasonRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 8, 1),
                1,
                "obs"
        );
    }

    @Test
    void createSeason_happyPath_returnsGeneratedId() {
        SeasonRequest req = validRequest();
        UUID generated = UUID.randomUUID();
        when(terrainGrpcClient.checkTerrainExists(req.terrain_id())).thenReturn(true);
        when(cropGrpcClient.checkCropExists(req.crop_id())).thenReturn(true);
        when(seasonRepository.createSeason(req)).thenReturn(generated);

        UUID result = seasonService.createSeason(req);

        assertThat(result).isEqualTo(generated);
        verify(seasonRepository).createSeason(req);
    }

    @Test
    void createSeason_terrainNotFound_throwsTerrainNotFoundException() {
        SeasonRequest req = validRequest();
        when(terrainGrpcClient.checkTerrainExists(req.terrain_id())).thenReturn(false);
        when(i18nService.getMessage(anyString(), any())).thenReturn("terrain not found");

        assertThatThrownBy(() -> seasonService.createSeason(req))
                .isInstanceOf(TerrainNotFoundException.class)
                .hasMessage("terrain not found");

        verify(seasonRepository, never()).createSeason(any());
        verify(cropGrpcClient, never()).checkCropExists(any());
    }

    @Test
    void createSeason_cropNotFound_throwsCropNotFoundException() {
        SeasonRequest req = validRequest();
        when(terrainGrpcClient.checkTerrainExists(req.terrain_id())).thenReturn(true);
        when(cropGrpcClient.checkCropExists(req.crop_id())).thenReturn(false);
        when(i18nService.getMessage(anyString(), any())).thenReturn("crop not found");

        assertThatThrownBy(() -> seasonService.createSeason(req))
                .isInstanceOf(CropNotFoundException.class)
                .hasMessage("crop not found");

        verify(seasonRepository, never()).createSeason(any());
    }

    @Test
    void createSeason_terrainCheckedBeforeCrop() {
        SeasonRequest req = validRequest();
        when(terrainGrpcClient.checkTerrainExists(req.terrain_id())).thenReturn(false);
        when(i18nService.getMessage(anyString(), any())).thenReturn("terrain not found");

        // Both would fail, but only terrain check should run
        assertThatThrownBy(() -> seasonService.createSeason(req))
                .isInstanceOf(TerrainNotFoundException.class);

        verify(terrainGrpcClient).checkTerrainExists(req.terrain_id());
        verify(cropGrpcClient, never()).checkCropExists(any());
    }

    @Test
    void deleteSeason_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        seasonService.deleteSeason(id);

        verify(seasonRepository).deleteSeason(id);
    }

    @Test
    void deleteSeasonsByTerrainId_delegatesToRepository() {
        UUID terrainId = UUID.randomUUID();

        seasonService.deleteSeasonsByTerrainId(terrainId);

        verify(seasonRepository).deleteByTerrainId(terrainId);
    }

    @Test
    void getSeason_delegatesToRepositoryWithFormattedFields() {
        UUID id = UUID.randomUUID();
        List<SeasonField> fields = List.of(SeasonField.id, SeasonField.terrain_id);
        when(fieldsValidator.formatFieldList(fields)).thenReturn("id,terrain_id");
        doReturn(Map.of("id", id))
                .when(seasonRepository).getSeason(id, "id,terrain_id");

        Object result = seasonService.getSeason(id, fields);

        assertThat(result).isInstanceOf(Map.class);
        verify(fieldsValidator).formatFieldList(fields);
        verify(seasonRepository).getSeason(id, "id,terrain_id");
    }

    @Test
    void getSeasonsByTerrain_delegatesToRepository() {
        UUID terrainId = UUID.randomUUID();
        List<SeasonField> fields = List.of(SeasonField.id);
        when(fieldsValidator.formatFieldList(fields)).thenReturn("id");
        doReturn(List.of(Map.of("id", UUID.randomUUID())))
                .when(seasonRepository).getSeasonsByTerrain(terrainId, "id");

        var result = seasonService.getSeasonsByTerrain(terrainId, fields);

        assertThat(result).hasSize(1);
        verify(seasonRepository).getSeasonsByTerrain(terrainId, "id");
    }
}
