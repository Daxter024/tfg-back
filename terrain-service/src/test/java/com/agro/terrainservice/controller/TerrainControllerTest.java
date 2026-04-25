package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.TerrainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TerrainController.class, excludeAutoConfiguration = { SecurityAutoConfiguration.class })
@Import(GlobalExceptionHandler.class)
class TerrainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TerrainService terrainService;

    @MockitoBean
    private I18nService i18nService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getTerrains_ShouldReturnList_WhenUserIdProvided() throws Exception {
        UUID userId = UUID.randomUUID();
        when(terrainService.getTerrains(any(UUID.class), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/terrain")
                .param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getTerrain_ShouldReturnTerrain_WhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("id", id.toString());
        mockResponse.put("name", "Test Terrain");

        when(terrainService.getTerrain(any(UUID.class), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/terrain/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(mockResponse)));
    }

    @Test
    void create_ShouldReturnCreated_WhenValidRequest() throws Exception {
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", Collections.emptyList());

        TerrainRequest request = new TerrainRequest(
                "New Terrain",
                UUID.randomUUID(),
                geometry);

        when(terrainService.create(any(TerrainRequest.class)))
                .thenReturn("Terrain created successfully");

        mockMvc.perform(post("/terrain")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string("Terrain created successfully"));
    }

    @Test
    void delete_ReturnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doNothing().when(terrainService).deleteTerrain(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/terrain/{id}", id)
                .param("user_id", userId.toString()))
                .andExpect(status().isNoContent());
    }
}
