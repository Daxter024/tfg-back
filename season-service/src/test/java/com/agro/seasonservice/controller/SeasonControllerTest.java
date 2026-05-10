package com.agro.seasonservice.controller;

import com.agro.seasonservice.exception.CropNotFoundException;
import com.agro.seasonservice.exception.GlobalExceptionHandler;
import com.agro.seasonservice.exception.ResourceNotFoundException;
import com.agro.seasonservice.exception.TerrainNotFoundException;
import com.agro.seasonservice.service.SeasonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeasonControllerTest {

    @Mock
    private SeasonService seasonService;

    @InjectMocks
    private SeasonController seasonController;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(seasonController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String validBody(UUID terrainId, UUID cropId) {
        return """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-03-01","end_date":"2025-08-01","season_type_id":1,"observations":"obs"}
                """.formatted(terrainId, cropId);
    }

    @Test
    void createSeason_happyPath_returns201WithUuid() throws Exception {
        UUID generated = UUID.randomUUID();
        when(seasonService.createSeason(any())).thenReturn(generated);

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(validBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(content().string("\"" + generated + "\""));
    }

    @Test
    void createSeason_invalidBody_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createSeason_endBeforeStart_returns400() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-08-01","end_date":"2025-03-01","season_type_id":1}
                """.formatted(t, c);

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSeason_terrainNotFound_returns404WithTitle() throws Exception {
        when(seasonService.createSeason(any()))
                .thenThrow(new TerrainNotFoundException("terrain X does not exist"));

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(validBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"))
                .andExpect(jsonPath("$.detail").value("terrain X does not exist"));
    }

    @Test
    void createSeason_cropNotFound_returns404WithTitle() throws Exception {
        when(seasonService.createSeason(any()))
                .thenThrow(new CropNotFoundException("crop X does not exist"));

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(validBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Crop not found"));
    }

    @Test
    void getSeason_returnsMap() throws Exception {
        UUID id = UUID.randomUUID();
        doReturn(Map.of("id", id.toString()))
                .when(seasonService).getSeason(any(), any());

        mockMvc.perform(get("/season/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getSeasonsByTerrain_returnsList() throws Exception {
        UUID terrainId = UUID.randomUUID();
        doReturn(List.of(Map.of("id", "a"), Map.of("id", "b")))
                .when(seasonService).getSeasonsByTerrain(any(), any());

        mockMvc.perform(get("/season/terrain/{terrainId}", terrainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deleteSeason_returns204NoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/season/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(seasonService).deleteSeason(id);
    }

    @Test
    void deleteSeason_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("season X not found"))
                .when(seasonService).deleteSeason(id);

        mockMvc.perform(delete("/season/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}
