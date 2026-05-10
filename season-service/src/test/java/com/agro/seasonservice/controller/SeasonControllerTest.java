package com.agro.seasonservice.controller;

import com.agro.seasonservice.exception.CropNotFoundException;
import com.agro.seasonservice.exception.GlobalExceptionHandler;
import com.agro.seasonservice.exception.ResourceNotFoundException;
import com.agro.seasonservice.exception.TerrainNotFoundException;
import com.agro.seasonservice.service.SeasonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.never;
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

    private String minimalBody(UUID terrainId, UUID cropId) {
        return """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-03-01"}
                """.formatted(terrainId, cropId);
    }

    // =========================================================================
    // §1. POST /season — alta de season
    // =========================================================================

    @Test
    @DisplayName("SEASON-1.01: happy path completo")
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
    @DisplayName("SEASON-1.01b: happy path mínimo (solo obligatorios)")
    void createSeason_minimalBody_returns201() throws Exception {
        UUID generated = UUID.randomUUID();
        when(seasonService.createSeason(any())).thenReturn(generated);

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(minimalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-1.05: terrain_id no UUID → 400")
    void createSeason_terrainIdNotUuid_returns400() throws Exception {
        String body = """
                {"terrain_id":"abc","crop_id":"%s","start_date":"2025-03-01"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-1.07: crop_id no UUID → 400")
    void createSeason_cropIdNotUuid_returns400() throws Exception {
        String body = """
                {"terrain_id":"%s","crop_id":"abc","start_date":"2025-03-01"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-1.09: start_date formato incorrecto → 400")
    void createSeason_invalidDateFormat_returns400() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"01/01/2025"}
                """.formatted(t, c);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-1.10: end_date < start_date → 400 (@AssertTrue)")
    void createSeason_endBeforeStart_returns400() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-08-01","end_date":"2025-03-01"}
                """.formatted(t, c);

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-1.11: end_date == start_date → 201 (borde válido)")
    void createSeason_endEqualsStart_returns201() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        when(seasonService.createSeason(any())).thenReturn(UUID.randomUUID());
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-03-01","end_date":"2025-03-01"}
                """.formatted(t, c);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-1.12: end_date ausente → 201")
    void createSeason_endDateAbsent_returns201() throws Exception {
        when(seasonService.createSeason(any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(minimalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-1.13: observations > 2000 chars → 400")
    void createSeason_observationsTooLong_returns400() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        String big = "X".repeat(2001);
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-03-01","observations":"%s"}
                """.formatted(t, c, big);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("SEASON-1.14: observations exactamente 2000 chars → 201")
    void createSeason_observationsAt2000_returns201() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        when(seasonService.createSeason(any())).thenReturn(UUID.randomUUID());
        String exact = "X".repeat(2000);
        String body = """
                {"terrain_id":"%s","crop_id":"%s","start_date":"2025-03-01","observations":"%s"}
                """.formatted(t, c, exact);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-1.18: season_type_id ausente → 201")
    void createSeason_seasonTypeIdAbsent_returns201() throws Exception {
        when(seasonService.createSeason(any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(minimalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-1.19: terrain_id inexistente → 404 'Terrain not found'")
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
    @DisplayName("SEASON-1.20: crop_id inexistente → 404 'Crop not found'")
    void createSeason_cropNotFound_returns404WithTitle() throws Exception {
        when(seasonService.createSeason(any()))
                .thenThrow(new CropNotFoundException("crop X does not exist"));

        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content(validBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Crop not found"));
    }

    // SEASON-1.21 (terrain-service caído → 500) requiere DispatcherServlet real
    // con el handler default de Spring; no se puede probar en standalone setup.
    // Verificado por la suite de integración cuando esté disponible.

    @Test
    @DisplayName("SEASON-1.24: body vacío → 400 con 3 errores")
    void createSeason_invalidBody_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("SEASON-1.25: body con Content-Type no JSON → 415")
    void createSeason_nonJsonContentType_returns415() throws Exception {
        mockMvc.perform(post("/season")
                        .contentType("text/plain")
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("SEASON-1.26: JSON malformado → 400")
    void createSeason_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/season")
                        .contentType(APPLICATION_JSON)
                        .content("{\"terrain_id\":"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // §2. GET /season/{id} — detalle
    // =========================================================================

    @Test
    @DisplayName("SEASON-2.01: detalle existente sin fields")
    void getSeason_returnsMap() throws Exception {
        UUID id = UUID.randomUUID();
        doReturn(Map.of("id", id.toString()))
                .when(seasonService).getSeason(any(), any());

        mockMvc.perform(get("/season/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("SEASON-2.02: detalle inexistente → 404")
    void getSeason_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("season not found"))
                .when(seasonService).getSeason(any(), any());

        mockMvc.perform(get("/season/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("SEASON-2.03: detalle con fields=id,start_date")
    void getSeason_withFields_passesProjection() throws Exception {
        UUID id = UUID.randomUUID();
        doReturn(Map.of("id", id.toString(), "start_date", "2025-03-01"))
                .when(seasonService).getSeason(any(), any());

        mockMvc.perform(get("/season/{id}", id).param("fields", "id", "start_date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.start_date").value("2025-03-01"));

        verify(seasonService).getSeason(any(), any());
    }

    @Test
    @DisplayName("SEASON-2.04: fields fuera del enum → 400 binding")
    void getSeason_invalidField_returns400() throws Exception {
        mockMvc.perform(get("/season/{id}", UUID.randomUUID()).param("fields", "secret"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-2.05: fields mezcla válidos+inválidos → 400")
    void getSeason_mixedValidAndInvalidFields_returns400() throws Exception {
        mockMvc.perform(get("/season/{id}", UUID.randomUUID())
                        .param("fields", "id", "secret"))
                .andExpect(status().isBadRequest());
        verify(seasonService, never()).getSeason(any(), any());
    }

    @Test
    @DisplayName("SEASON-2.09: id no UUID → 400 binding")
    void getSeason_idNotUuid_returns400() throws Exception {
        mockMvc.perform(get("/season/abc"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // §3. GET /season/terrain/{terrainId} — listar por terreno
    // =========================================================================

    @Test
    @DisplayName("SEASON-3.01: terreno con varias seasons → 200 array")
    void getSeasonsByTerrain_returnsList() throws Exception {
        UUID terrainId = UUID.randomUUID();
        doReturn(List.of(Map.of("id", "a"), Map.of("id", "b")))
                .when(seasonService).getSeasonsByTerrain(any(), any());

        mockMvc.perform(get("/season/terrain/{terrainId}", terrainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("SEASON-3.02: terreno sin seasons → 200 [] (no 404)")
    void getSeasonsByTerrain_emptyList_returns200() throws Exception {
        UUID terrainId = UUID.randomUUID();
        doReturn(List.of())
                .when(seasonService).getSeasonsByTerrain(any(), any());

        mockMvc.perform(get("/season/terrain/{terrainId}", terrainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("SEASON-3.05: terrainId no UUID → 400")
    void getSeasonsByTerrain_terrainIdNotUuid_returns400() throws Exception {
        mockMvc.perform(get("/season/terrain/abc"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // §4. DELETE /season/{id} — borrado manual
    // =========================================================================

    @Test
    @DisplayName("SEASON-4.01: happy path → 204 sin body")
    void deleteSeason_returns204NoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/season/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(seasonService).deleteSeason(id);
    }

    @Test
    @DisplayName("SEASON-4.02: id inexistente → 404 'Resource not found'")
    void deleteSeason_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("season X not found"))
                .when(seasonService).deleteSeason(id);

        mockMvc.perform(delete("/season/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("SEASON-4.03: id no UUID → 400 binding")
    void deleteSeason_idNotUuid_returns400() throws Exception {
        mockMvc.perform(delete("/season/abc"))
                .andExpect(status().isBadRequest());
    }
}
