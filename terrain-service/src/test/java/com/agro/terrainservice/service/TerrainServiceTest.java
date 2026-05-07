package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.UserNotFoundException;
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

    @InjectMocks
    private TerrainService terrainService;

    private TerrainRequest validRequest;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        validRequest = new TerrainRequest(
                "Test Terrain",
                userId,
                geometry,
                SoilType.franco,
                12.5,
                IrrigationType.goteo,
                "1234ABCD5678EF"
        );

        // Stubs i18n por defecto (lenient).
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_savesTerrain_whenUserIsValid() throws JsonProcessingException {
        UUID newId = UUID.randomUUID();
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        when(terrainRepository.saveWithCalculations(
                eq("Test Terrain"), eq(userId), anyString(),
                eq(SoilType.franco), eq(12.5), eq(IrrigationType.goteo), eq("1234ABCD5678EF")
        )).thenReturn(newId);

        UUID result = terrainService.create(validRequest);

        verify(terrainRepository).saveWithCalculations(
                eq("Test Terrain"), eq(userId), anyString(),
                eq(SoilType.franco), eq(12.5), eq(IrrigationType.goteo), eq("1234ABCD5678EF")
        );
        assertEquals(newId, result);
    }

    @Test
    void create_throwsUserNotFound_whenUserIsInvalid() {
        when(userGrpcClient.validateUser(userId)).thenReturn(false);

        UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                () -> terrainService.create(validRequest));
        // El mensaje viene de la clave i18n; nuestro stub devuelve la propia clave.
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("user.notfound"));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsInvalidGeometry_whenJsonSerializationFails() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(validRequest));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsAreaOutOfRange_whenDbConstraintFires() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "violates check constraint \"terrain_area_range\"",
                new RuntimeException("ERROR: new row violates check constraint \"terrain_area_range\"")
        );
        when(terrainRepository.saveWithCalculations(
                any(), any(), anyString(), any(), any(), any(), any()
        )).thenThrow(dive);

        assertThrows(AreaOutOfRangeException.class, () -> terrainService.create(validRequest));
    }

    @Test
    void create_throwsInvalidGeometry_whenSridOrPolygonConstraintFires() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "violates check constraint \"terrain_geom_srid\"",
                new RuntimeException("ERROR: new row violates check constraint \"terrain_geom_srid\"")
        );
        when(terrainRepository.saveWithCalculations(
                any(), any(), anyString(), any(), any(), any(), any()
        )).thenThrow(dive);

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(validRequest));
    }

    @Test
    void getTerrain_returnsTerrainMap() {
        UUID id = UUID.randomUUID();
        Map<String, Object> expected = new HashMap<>();
        expected.put("id", id);

        when(fieldsValidator.formatFieldList(any())).thenReturn("*");
        when(terrainRepository.getTerrain(id, "*")).thenReturn(expected);

        Map<String, Object> result = terrainService.getTerrain(id, null);

        assertEquals(expected, result);
    }

    @Test
    void deleteTerrain_publishesEvent() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        terrainService.deleteTerrain(id, owner);

        verify(terrainRepository).deleteTerrain(id, owner);
        verify(eventPublisher).publishTerrainDeleted(any(com.agro.terrainservice.event.TerrainDeletedEvent.class));
    }
}
