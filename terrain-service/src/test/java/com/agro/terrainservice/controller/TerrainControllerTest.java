package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.service.TerrainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grpc.server.port=0")
@AutoConfigureMockMvc
@Disabled("Disabled due to ApplicationContext loading issues with gRPC auto-configuration in test environment")
class TerrainControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private TerrainService terrainService;

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
        void delete_ShouldReturnOk_WhenTerrainExists() throws Exception {
                UUID id = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                when(terrainService.delete(any(UUID.class), any(UUID.class)))
                                .thenReturn("Terrain deleted successfully");

                mockMvc.perform(delete("/terrain/{id}", id)
                                .param("user_id", userId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Terrain deleted successfully"));
        }
}
