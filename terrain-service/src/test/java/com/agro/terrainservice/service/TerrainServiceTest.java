package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.event.TerrainDeletedEvent;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.exception.UserNotFoundException;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldsValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("TER-1.01 unit - create persiste cuando user es valido")
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
    @DisplayName("TER-1.07 - user_id inexistente en auth-service dispara UserNotFoundException")
    void create_throwsUserNotFound_whenUserIsInvalid() {
        when(userGrpcClient.validateUser(userId)).thenReturn(false);

        UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                () -> terrainService.create(validRequest));
        assertTrue(ex.getMessage().contains("user.notfound"));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TER-1.28 - Idempotencia de error: no persiste cuando gRPC dice false")
    void create_doesNotPersist_whenUserNotFound() {
        when(userGrpcClient.validateUser(userId)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> terrainService.create(validRequest));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TER-1.09 - geometry no serializable dispara InvalidGeometryException")
    void create_throwsInvalidGeometry_whenJsonSerializationFails() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});

        InvalidGeometryException ex = assertThrows(InvalidGeometryException.class,
                () -> terrainService.create(validRequest));
        assertTrue(ex.getMessage().contains("error.geojson"));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TER-1.29 - Idempotencia: no persiste cuando serializacion falla")
    void create_doesNotPersist_whenSerializationFails() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(validRequest));
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TER-1.12 - DB rechaza area fuera de rango dispara AreaOutOfRangeException")
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

        AreaOutOfRangeException ex = assertThrows(AreaOutOfRangeException.class,
                () -> terrainService.create(validRequest));
        assertTrue(ex.getMessage().contains("terrain.area.out.of.range"));
    }

    @Test
    @DisplayName("TER-1.13 - DB rechaza area > 1e8 m^2 (mismo path)")
    void create_throwsAreaOutOfRange_whenAreaTooLarge() throws JsonProcessingException {
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
    @DisplayName("TER-1.10 - SRID != 4326 dispara InvalidGeometryException")
    void create_throwsInvalidGeometry_whenSridConstraintFires() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "violates check constraint \"terrain_geom_srid\"",
                new RuntimeException("ERROR: new row violates check constraint \"terrain_geom_srid\"")
        );
        when(terrainRepository.saveWithCalculations(
                any(), any(), anyString(), any(), any(), any(), any()
        )).thenThrow(dive);

        InvalidGeometryException ex = assertThrows(InvalidGeometryException.class,
                () -> terrainService.create(validRequest));
        assertTrue(ex.getMessage().contains("terrain.geometry.invalid"));
    }

    @Test
    @DisplayName("TER-1.11 - geometria invalida (auto-intersectada) dispara InvalidGeometry")
    void create_throwsInvalidGeometry_whenGeomValidConstraintFires() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "violates check constraint \"terrain_geom_valid\"",
                new RuntimeException("ERROR: new row violates check constraint \"terrain_geom_valid\"")
        );
        when(terrainRepository.saveWithCalculations(
                any(), any(), anyString(), any(), any(), any(), any()
        )).thenThrow(dive);

        assertThrows(InvalidGeometryException.class, () -> terrainService.create(validRequest));
    }

    @Test
    @DisplayName("TER-1.27 - DataIntegrityViolation no reconocida se propaga")
    void create_propagatesUnknownDataIntegrityViolation() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "Some other constraint",
                new RuntimeException("ERROR: some other constraint")
        );
        when(terrainRepository.saveWithCalculations(
                any(), any(), anyString(), any(), any(), any(), any()
        )).thenThrow(dive);

        assertThrows(DataIntegrityViolationException.class, () -> terrainService.create(validRequest));
    }

    @Test
    @DisplayName("TER-2.05 - getTerrain devuelve mapa con campos seleccionados")
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
    @DisplayName("TER-2.02 - getTerrains delega en repository con user_id correcto")
    void getTerrains_delegatesWithUserId() {
        UUID uid = UUID.randomUUID();
        when(fieldsValidator.formatFieldList(any())).thenReturn("*");
        when(terrainRepository.getTerrains(uid, "*"))
                .thenReturn(List.of(Map.of("id", "a"), Map.of("id", "b"), Map.of("id", "c")));

        List<Map<String, Object>> rows = terrainService.getTerrains(uid, null);
        assertEquals(3, rows.size());
        verify(terrainRepository).getTerrains(uid, "*");
    }

    @Test
    @DisplayName("TER-3.01 - deleteTerrain publica TerrainDeletedEvent")
    void deleteTerrain_publishesEvent() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        terrainService.deleteTerrain(id, owner);

        verify(terrainRepository).deleteTerrain(id, owner);
        verify(eventPublisher).publishTerrainDeleted(any(TerrainDeletedEvent.class));
    }

    @Test
    @DisplayName("TER-3.02 - DELETE por usuario equivocado: no publica evento si repo lanza")
    void deleteTerrain_doesNotPublishEvent_whenRepoThrows() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new TerrainNotFoundException("terrain.notfound"))
                .when(terrainRepository).deleteTerrain(id, owner);

        assertThrows(TerrainNotFoundException.class, () -> terrainService.deleteTerrain(id, owner));
        verify(eventPublisher, never()).publishTerrainDeleted(any());
    }

    @Test
    @DisplayName("TER-11.02 - deleteTerrainsByUserId borra cada uno y publica evento")
    void deleteTerrainsByUserId_deletesAllAndPublishesEvents() {
        UUID uid = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(terrainRepository.findIdsByUserId(uid)).thenReturn(List.of(a, b));

        terrainService.deleteTerrainsByUserId(uid);

        verify(terrainRepository).deleteById(a);
        verify(terrainRepository).deleteById(b);
        verify(eventPublisher, times(2)).publishTerrainDeleted(any(TerrainDeletedEvent.class));
    }

    @Test
    @DisplayName("TER-11.01 - deleteTerrainsByUserId con 0 terrenos no publica nada")
    void deleteTerrainsByUserId_noEventsWhenEmpty() {
        UUID uid = UUID.randomUUID();
        when(terrainRepository.findIdsByUserId(uid)).thenReturn(List.of());

        terrainService.deleteTerrainsByUserId(uid);

        verify(eventPublisher, never()).publishTerrainDeleted(any());
    }

    @Test
    @DisplayName("HU-TER-03 helper - existsForUser true cuando user_id coincide")
    void existsForUser_returnsTrue_whenOwnerMatches() {
        UUID terrainId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(terrainRepository.getTerrain(terrainId, "id, user_id"))
                .thenReturn(Map.of("id", terrainId, "user_id", owner));

        assertTrue(terrainService.existsForUser(terrainId, owner));
    }

    @Test
    @DisplayName("HU-TER-03 helper - existsForUser false cuando user_id distinto")
    void existsForUser_returnsFalse_whenOwnerMismatch() {
        UUID terrainId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(terrainRepository.getTerrain(terrainId, "id, user_id"))
                .thenReturn(Map.of("id", terrainId, "user_id", owner));

        assertFalse(terrainService.existsForUser(terrainId, other));
    }

    @Test
    @DisplayName("HU-TER-03 helper - existsForUser false cuando terreno no existe")
    void existsForUser_returnsFalse_whenTerrainMissing() {
        UUID terrainId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(terrainRepository.getTerrain(terrainId, "id, user_id"))
                .thenThrow(new TerrainNotFoundException("terrain.notfound"));

        assertFalse(terrainService.existsForUser(terrainId, owner));
    }
}
