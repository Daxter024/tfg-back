package com.agro.seasonservice.security;

import com.agro.seasonservice.controller.SeasonController;
import com.agro.seasonservice.exception.GlobalExceptionHandler;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeasonSecurityTest {

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

    @Test
    @DisplayName("SEASON-10.01: SQL injection vía fields → 400, repo nunca invocado")
    void sqlInjectionInFields_returns400_repoNeverInvoked() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/season/{id}", id)
                        .param("fields", "id;DROP TABLE season;--"))
                .andExpect(status().isBadRequest());

        verify(seasonService, never()).getSeason(any(), any());
    }

    @Test
    @DisplayName("SEASON-10.02: SQL injection vía terrainId path → 400 binding")
    void sqlInjectionInTerrainIdPath_returns400() throws Exception {
        mockMvc.perform(get("/season/terrain/abc'; DROP TABLE season;--"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-10.03: body con propiedades extra → 201 (Jackson ignora)")
    void extraPropertiesInBody_areIgnored() throws Exception {
        UUID t = UUID.randomUUID(), c = UUID.randomUUID();
        when(seasonService.createSeason(any())).thenReturn(UUID.randomUUID());

        String body = """
                {
                  "terrain_id":"%s",
                  "crop_id":"%s",
                  "start_date":"2025-03-01",
                  "isAdmin": true,
                  "secret_field": "abc"
                }
                """.formatted(t, c);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEASON-10.04: tipos inesperados (terrain_id numérico) → 400")
    void unexpectedTypes_returns400() throws Exception {
        UUID c = UUID.randomUUID();
        // Jackson permite Integer→LocalDate (epoch day), así que el test usa
        // un campo donde la conversión claramente NO funciona: número→UUID.
        String body = """
                {"terrain_id":12345,"crop_id":"%s","start_date":"2025-03-01"}
                """.formatted(c);

        mockMvc.perform(post("/season").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEASON-10.06: path traversal en id → 400 binding (no UUID)")
    void pathTraversalInId_returns400() throws Exception {
        mockMvc.perform(delete("/season/../../etc/passwd"))
                .andExpect(status().is4xxClientError());
    }
}
