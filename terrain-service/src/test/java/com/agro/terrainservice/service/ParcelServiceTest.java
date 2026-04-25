package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.event.ParcelDeletedEvent;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.ParcelNotFoundException;
import com.agro.terrainservice.exception.ParcelNotWithinTerrainException;
import com.agro.terrainservice.exception.ParcelOverlapsException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.repository.ParcelRepository;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.ParcelFieldsValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParcelServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private TerrainRepository terrainRepository;

    @Mock
    private ParcelFieldsValidator fieldsValidator;

    @Mock
    private I18nService i18nService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ParcelService parcelService;

    private final UUID terrainId = UUID.randomUUID();

    private static final Map<String, Object> VALID_GEOMETRY = Map.of(
            "type", "Polygon",
            "coordinates", List.of(List.of(
                    List.of(0.0, 0.0),
                    List.of(0.0, 0.01),
                    List.of(0.01, 0.01),
                    List.of(0.01, 0.0),
                    List.of(0.0, 0.0)
            ))
    );

    @BeforeEach
    void setUp() throws Exception {
        when(i18nService.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(any(String.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainRepository.existsById(terrainId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
    }

    @Test
    void create_ShouldThrow_WhenTerrainDoesNotExist() {
        when(terrainRepository.existsById(terrainId)).thenReturn(false);
        ParcelRequest req = new ParcelRequest("P1", VALID_GEOMETRY);

        assertThrows(TerrainNotFoundException.class,
                () -> parcelService.create(terrainId, req));
    }

    @Test
    void create_ShouldThrow_WhenParcelNotWithinTerrain() {
        ParcelRequest req = new ParcelRequest("P1", VALID_GEOMETRY);
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(false);

        assertThrows(ParcelNotWithinTerrainException.class,
                () -> parcelService.create(terrainId, req));
        verify(parcelRepository, never()).save(any(), any(), any());
    }

    @Test
    void create_ShouldThrow_WhenParcelOverlapsExisting() {
        ParcelRequest req = new ParcelRequest("P1", VALID_GEOMETRY);
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlappingParcelName(eq(terrainId), anyString(), eq(null)))
                .thenReturn("P-existing");

        assertThrows(ParcelOverlapsException.class,
                () -> parcelService.create(terrainId, req));
    }

    @Test
    void create_ShouldThrowInvalidGeometry_WhenStructuralChecksFail() {
        Map<String, Object> bad = Map.of(
                "type", "Point",
                "coordinates", List.of(0.0, 0.0)
        );
        ParcelRequest req = new ParcelRequest("P1", bad);

        assertThrows(InvalidGeometryException.class,
                () -> parcelService.create(terrainId, req));
    }

    @Test
    void create_ShouldPersist_WhenValid() {
        ParcelRequest req = new ParcelRequest("P1", VALID_GEOMETRY);
        UUID newId = UUID.randomUUID();
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlappingParcelName(eq(terrainId), anyString(), eq(null)))
                .thenReturn(null);
        when(parcelRepository.save(eq(terrainId), eq("P1"), anyString())).thenReturn(newId);

        UUID id = parcelService.create(terrainId, req);
        assertEquals(newId, id);
    }

    @Test
    void update_ShouldRecomputeArea_WhenGeometryChanged() {
        UUID parcelId = UUID.randomUUID();
        ParcelUpdateRequest req = new ParcelUpdateRequest(null, VALID_GEOMETRY);
        when(parcelRepository.getParcel(eq(terrainId), eq(parcelId), anyString()))
                .thenReturn(Map.of("id", parcelId));
        when(parcelRepository.isWithinTerrain(eq(terrainId), anyString())).thenReturn(true);
        when(parcelRepository.findOverlappingParcelName(eq(terrainId), anyString(), eq(parcelId)))
                .thenReturn(null);

        parcelService.update(terrainId, parcelId, req);

        verify(parcelRepository).updateGeometry(eq(parcelId), eq(terrainId), anyString());
    }

    @Test
    void update_ShouldRejectEmptyPayload() {
        UUID parcelId = UUID.randomUUID();
        ParcelUpdateRequest empty = new ParcelUpdateRequest(null, null);
        assertThrows(IllegalArgumentException.class,
                () -> parcelService.update(terrainId, parcelId, empty));
    }

    @Test
    void delete_ShouldEmitEvent_WhenParcelExists() {
        UUID parcelId = UUID.randomUUID();
        when(parcelRepository.deleteById(parcelId, terrainId)).thenReturn(1);

        parcelService.delete(terrainId, parcelId);

        ArgumentCaptor<ParcelDeletedEvent> captor =
                ArgumentCaptor.forClass(ParcelDeletedEvent.class);
        verify(eventPublisher).publishParcelDeleted(captor.capture());
        ParcelDeletedEvent event = captor.getValue();
        assertEquals(parcelId, event.parcelId());
        assertEquals(terrainId, event.terrainId());
    }

    @Test
    void delete_ShouldThrow_WhenParcelDoesNotExist() {
        UUID parcelId = UUID.randomUUID();
        when(parcelRepository.deleteById(parcelId, terrainId)).thenReturn(0);

        assertThrows(ParcelNotFoundException.class,
                () -> parcelService.delete(terrainId, parcelId));
    }

    @Test
    void publishCascadeForTerrainDeletion_EmitsOneEventPerParcel() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(parcelRepository.findIdsByTerrainId(terrainId)).thenReturn(List.of(p1, p2));

        parcelService.publishCascadeForTerrainDeletion(terrainId);

        verify(eventPublisher).publishParcelDeleted(new ParcelDeletedEvent(p1, terrainId));
        verify(eventPublisher).publishParcelDeleted(new ParcelDeletedEvent(p2, terrainId));
    }

    @Test
    void computeSummary_ReturnsCountAreasAndUnparceled() {
        when(parcelRepository.countByTerrainId(terrainId)).thenReturn(2);
        when(parcelRepository.sumAreaByTerrainId(terrainId)).thenReturn(7500d);

        Map<String, Object> summary = parcelService.computeSummary(terrainId, 10000d);

        assertEquals(2, summary.get("parcel_count"));
        assertEquals(7500d, (double) summary.get("area_parceled_m2"), 0.0001);
        assertEquals(2500d, (double) summary.get("area_unparceled_m2"), 0.0001);
    }
}
