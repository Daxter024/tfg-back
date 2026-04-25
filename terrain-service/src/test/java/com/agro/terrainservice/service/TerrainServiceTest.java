package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldsValidator;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerrainServiceTest {

    @Mock
    private TerrainRepository terrainRepository;

    @Mock
    private I18nService i18nService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private FieldsValidator fieldsValidator;

    @Mock
    private UserGrpcClient userGrpcClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ParcelService parcelService;

    @InjectMocks
    private TerrainService terrainService;

    private final UUID userId = UUID.randomUUID();

    private static final String VALID_POLYGON_JSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,0.01],[0.01,0.01],[0.01,0],[0,0]]]}";

    /**
     * Polígono GeoJSON válido (cuadrado pequeño) — área en orden de magnitud
     * de varias hectáreas en EPSG:4326 al pasar a meters.
     */
    private Map<String, Object> validGeometry() {
        Map<String, Object> g = new HashMap<>();
        g.put("type", "Polygon");
        g.put("coordinates", List.of(List.of(
                List.of(0.0, 0.0),
                List.of(0.0, 0.01),
                List.of(0.01, 0.01),
                List.of(0.01, 0.0),
                List.of(0.0, 0.0)
        )));
        return g;
    }

    @BeforeEach
    void setUp() {
        // Stub i18n por defecto para devolver la clave (deja al test concreto
        // sobreescribir si quiere comparar con un mensaje literal).
        when(i18nService.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(any(String.class), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_ShouldSaveTerrain_WhenUserIsValid() throws JsonProcessingException {
        TerrainRequest request = new TerrainRequest(
                "Test Terrain", userId, validGeometry(),
                SoilType.franco, 5.0, IrrigationType.goteo, "12345678901234"
        );
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn(VALID_POLYGON_JSON);

        String result = terrainService.create(request);

        verify(terrainRepository).saveWithCalculations(
                eq("Test Terrain"), eq(userId), any(),
                eq(SoilType.franco), eq(5.0), eq(IrrigationType.goteo), eq("12345678901234")
        );
        assertEquals("terrain.created", result);
    }

    @Test
    void create_ShouldThrowException_WhenUserIsInvalid() {
        TerrainRequest request = new TerrainRequest(
                "Test", userId, validGeometry(), null, null, null, null
        );
        when(userGrpcClient.validateUser(userId)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> terrainService.create(request));
        assertEquals("user.notfound", exception.getMessage());
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_ShouldThrowInvalidGeometry_WhenGeometryIsNotPolygon() {
        Map<String, Object> badGeom = Map.of(
                "type", "Point",
                "coordinates", List.of(0.0, 0.0)
        );
        TerrainRequest request = new TerrainRequest(
                "Test", userId, badGeom, null, null, null, null
        );
        when(userGrpcClient.validateUser(userId)).thenReturn(true);

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(request));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_ShouldThrowInvalidGeometry_WhenRingNotClosed() {
        Map<String, Object> openRing = Map.of(
                "type", "Polygon",
                "coordinates", List.of(List.of(
                        List.of(0.0, 0.0),
                        List.of(0.0, 0.01),
                        List.of(0.01, 0.01),
                        List.of(0.01, 0.0)
                        // missing closing vertex
                ))
        );
        TerrainRequest request = new TerrainRequest(
                "Test", userId, openRing, null, null, null, null
        );
        when(userGrpcClient.validateUser(userId)).thenReturn(true);

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(request));
    }

    @Test
    void create_ShouldThrowAreaOutOfRange_WhenDbConstraintRejects() throws JsonProcessingException {
        TerrainRequest request = new TerrainRequest(
                "Tiny", userId, validGeometry(), null, null, null, null
        );
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn(VALID_POLYGON_JSON);
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "violates check constraint \"terrain_area_range\""
        );
        when(terrainRepository.saveWithCalculations(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(dive);

        assertThrows(AreaOutOfRangeException.class, () -> terrainService.create(request));
    }

    @Test
    void getTerrain_ShouldReturnTerrainMap() {
        UUID id = UUID.randomUUID();
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("id", id);

        when(fieldsValidator.formatFieldList(any())).thenReturn("*");
        when(terrainRepository.getTerrain(id, "*")).thenReturn(expectedMap);

        Map<String, Object> result = terrainService.getTerrain(id, null);

        assertEquals(expectedMap, result);
    }

    @Test
    void deleteTerrain_ShouldPublishEvent() {
        UUID id = UUID.randomUUID();
        UUID delUserId = UUID.randomUUID();

        terrainService.deleteTerrain(id, delUserId);

        verify(terrainRepository).deleteTerrain(id, delUserId);
        verify(eventPublisher).publishTerrainDeleted(any(com.agro.terrainservice.event.TerrainDeletedEvent.class));
    }
}
