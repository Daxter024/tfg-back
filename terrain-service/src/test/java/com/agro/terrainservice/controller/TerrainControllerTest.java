package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.TerrainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TerrainController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class TerrainControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TerrainService terrainService;
    @MockitoBean private I18nService i18nService;

    @Test
    void getTerrains_returnsList_whenUserIdProvided() throws Exception {
        UUID userId = UUID.randomUUID();
        when(terrainService.getTerrains(any(UUID.class), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/terrain").param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getTerrain_returnsTerrain_whenExists() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("name", "Parcela Norte");
        when(terrainService.getTerrain(any(UUID.class), any())).thenReturn(body);

        mockMvc.perform(get("/terrain/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(body)));
    }

    @Test
    void create_returns201_withIdAndMessage() throws Exception {
        UUID newId = UUID.randomUUID();
        Map<String, Object> geometry = Map.of(
                "type", "Polygon",
                "coordinates", Collections.emptyList()
        );
        TerrainRequest request = new TerrainRequest(
                "Nuevo Terreno",
                UUID.randomUUID(),
                geometry,
                null, null, null, null
        );

        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("Terreno creado");

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newId.toString()))
                .andExpect(jsonPath("$.message").value("Terreno creado"));
    }

    @Test
    void create_returns400_whenInvalidPayload() throws Exception {
        // name vacio + geometry vacia -> dispara dos errores de @Valid
        String invalid = """
                {"name":"","user_id":"%s","geometry":{}}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doNothing().when(terrainService).deleteTerrain(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNoContent());
    }
}
