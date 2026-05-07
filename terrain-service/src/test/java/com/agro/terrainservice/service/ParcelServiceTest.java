package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.event.ParcelDeletedEvent;
import com.agro.terrainservice.exception.DuplicateParcelNameException;
import com.agro.terrainservice.exception.ParcelNotWithinTerrainException;
import com.agro.terrainservice.exception.ParcelOverlapException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.repository.ParcelRepository;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.ParcelFieldsValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParcelServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private TerrainRepository terrainRepository;
    @Mock private ParcelFieldsValidator parcelFieldsValidator;
    @Mock private ObjectMapper mapper;
    @Mock private I18nService i18nService;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks private ParcelService parcelService;

    private final UUID terrainId = UUID.randomUUID();
    private ParcelRequest validRequest;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        validRequest = new ParcelRequest("Sector A", geometry);

        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
        when(terrainRepository.existsById(terrainId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
    }

    @Test
    void create_throwsTerrainNotFound_whenTerrainMissing() {
        when(terrainRepository.existsById(terrainId)).thenReturn(false);

        assertThrows(TerrainNotFoundException.class,
                () -> parcelService.create(terrainId, validRequest));
        verify(parcelRepository, never()).insert(any(), any(), any());
    }

    @Test
    void create_throwsDuplicateName_whenNameAlreadyExists() {
        when(parcelRepository.nameExistsInTerrain(terrainId, "Sector A", null)).thenReturn(true);

        assertThrows(DuplicateParcelNameException.class,
                () -> parcelService.create(terrainId, validRequest));
        verify(parcelRepository, never()).insert(any(), any(), any());
    }

    @Test
    void create_throwsParcelNotWithin_whenContainmentFails() {
        when(parcelRepository.nameExistsInTerrain(eq(terrainId), anyString(), isNull())).thenReturn(false);
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(false);

        assertThrows(ParcelNotWithinTerrainException.class,
                () -> parcelService.create(terrainId, validRequest));
        verify(parcelRepository, never()).insert(any(), any(), any());
    }

    @Test
    void create_throwsParcelOverlap_whenAnotherParcelOverlaps() {
        when(parcelRepository.nameExistsInTerrain(eq(terrainId), anyString(), isNull())).thenReturn(false);
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlapping(eq(terrainId), anyString(), isNull()))
                .thenReturn(List.of("Sector B"));

        ParcelOverlapException ex = assertThrows(ParcelOverlapException.class,
                () -> parcelService.create(terrainId, validRequest));
        assertThat(ex.getMessage()).contains("parcel.overlaps");
    }

    @Test
    void create_returnsId_whenAllChecksPass() {
        when(parcelRepository.nameExistsInTerrain(eq(terrainId), anyString(), isNull())).thenReturn(false);
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlapping(eq(terrainId), anyString(), isNull())).thenReturn(List.of());
        UUID newId = UUID.randomUUID();
        when(parcelRepository.insert(eq(terrainId), eq("Sector A"), anyString())).thenReturn(newId);

        UUID id = parcelService.create(terrainId, validRequest);
        assertThat(id).isEqualTo(newId);
    }

    @Test
    void update_recalculatesGeometry_whenGeometryProvided() {
        UUID parcelId = UUID.randomUUID();
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        ParcelUpdateRequest dto = new ParcelUpdateRequest(null, geometry);

        when(parcelRepository.getParcel(eq(terrainId), eq(parcelId), anyString()))
                .thenReturn(Map.of("id", parcelId));
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlapping(eq(terrainId), anyString(), eq(parcelId))).thenReturn(List.of());

        parcelService.update(terrainId, parcelId, dto);

        verify(parcelRepository).updateGeometry(eq(terrainId), eq(parcelId), anyString());
        verify(parcelRepository, never()).updateName(any(), any(), anyString());
    }

    @Test
    void update_renames_whenOnlyNameProvided() {
        UUID parcelId = UUID.randomUUID();
        ParcelUpdateRequest dto = new ParcelUpdateRequest("Nuevo Nombre", null);

        when(parcelRepository.getParcel(eq(terrainId), eq(parcelId), anyString()))
                .thenReturn(Map.of("id", parcelId));
        when(parcelRepository.nameExistsInTerrain(eq(terrainId), eq("Nuevo Nombre"), eq(parcelId)))
                .thenReturn(false);

        parcelService.update(terrainId, parcelId, dto);

        verify(parcelRepository).updateName(eq(terrainId), eq(parcelId), eq("Nuevo Nombre"));
        verify(parcelRepository, never()).updateGeometry(any(), any(), anyString());
    }

    @Test
    void delete_emitsParcelDeletedEvent() {
        UUID parcelId = UUID.randomUUID();
        when(parcelRepository.getParcel(eq(terrainId), eq(parcelId), anyString()))
                .thenReturn(Map.of("id", parcelId));

        parcelService.delete(terrainId, parcelId);

        verify(parcelRepository).deleteParcel(terrainId, parcelId);
        verify(eventPublisher).publishParcelDeleted(any(ParcelDeletedEvent.class));
    }

    @Test
    void publishCascadeDeletesForTerrain_emitsOnePerParcel() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(parcelRepository.findIdsByTerrainId(terrainId)).thenReturn(List.of(p1, p2));

        parcelService.publishCascadeDeletesForTerrain(terrainId);

        verify(eventPublisher).publishParcelDeleted(new ParcelDeletedEvent(p1, terrainId));
        verify(eventPublisher).publishParcelDeleted(new ParcelDeletedEvent(p2, terrainId));
    }
}
