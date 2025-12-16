package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.dto.TerrainRequest;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private TerrainService terrainService;

    private TerrainRequest validRequest;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        validRequest = new TerrainRequest("Test Terrain", userId, geometry);
    }

    @Test
    void create_ShouldSaveTerrain_WhenUserIsValid() throws JsonProcessingException {
        when(userGrpcClient.validateUser(userId)).thenReturn(true);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"Polygon\"}");
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("Created");

        String result = terrainService.create(validRequest);

        verify(terrainRepository).saveWithCalculations(eq("Test Terrain"), eq(userId), anyString());
        assertEquals("Created", result);
    }

    @Test
    void create_ShouldThrowException_WhenUserIsInvalid() {
        when(userGrpcClient.validateUser(userId)).thenReturn(false);
        when(i18nService.getMessage(eq("user.notfound"), any())).thenReturn("User not found");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> terrainService.create(validRequest));
        assertEquals("User not found", exception.getMessage());
        verify(terrainRepository, never()).saveWithCalculations(any(), any(), any());
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
}
