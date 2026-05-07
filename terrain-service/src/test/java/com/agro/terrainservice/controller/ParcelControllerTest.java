package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.exception.ParcelOverlapException;
import com.agro.terrainservice.exception.ParcelNotWithinTerrainException;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.ParcelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ParcelController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class ParcelControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ParcelService parcelService;
    @MockitoBean private I18nService i18nService;

    @Test
    void list_returnsArray() throws Exception {
        UUID terrainId = UUID.randomUUID();
        when(parcelService.list(eq(terrainId), any())).thenReturn(List.of());

        mockMvc.perform(get("/terrain/{tid}/parcel", terrainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void create_returns201_withId() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelRequest body = new ParcelRequest("Sector A",
                Map.of("type", "Polygon", "coordinates", Collections.emptyList()));

        when(parcelService.create(eq(terrainId), any(ParcelRequest.class))).thenReturn(parcelId);
        when(i18nService.getMessage(eq("parcel.created"), any())).thenReturn("Parcela creada");

        mockMvc.perform(post("/terrain/{tid}/parcel", terrainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.message").value("Parcela creada"));
    }

    @Test
    void create_returns400_whenNotWithinTerrain() throws Exception {
        UUID terrainId = UUID.randomUUID();
        ParcelRequest body = new ParcelRequest("Sector A",
                Map.of("type", "Polygon", "coordinates", Collections.emptyList()));

        when(parcelService.create(eq(terrainId), any(ParcelRequest.class)))
                .thenThrow(new ParcelNotWithinTerrainException("not within"));

        mockMvc.perform(post("/terrain/{tid}/parcel", terrainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns409_whenOverlap() throws Exception {
        UUID terrainId = UUID.randomUUID();
        ParcelRequest body = new ParcelRequest("Sector A",
                Map.of("type", "Polygon", "coordinates", Collections.emptyList()));

        when(parcelService.create(eq(terrainId), any(ParcelRequest.class)))
                .thenThrow(new ParcelOverlapException("overlaps"));

        mockMvc.perform(post("/terrain/{tid}/parcel", terrainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns400_whenInvalidPayload() throws Exception {
        UUID terrainId = UUID.randomUUID();

        mockMvc.perform(post("/terrain/{tid}/parcel", terrainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"geometry\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        when(i18nService.getMessage(eq("parcel.updated"), any())).thenReturn("Parcela actualizada");

        mockMvc.perform(patch("/terrain/{tid}/parcel/{pid}", terrainId, parcelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sector B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Parcela actualizada"));
    }

    @Test
    void delete_returns204() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        doNothing().when(parcelService).delete(eq(terrainId), eq(parcelId));

        mockMvc.perform(delete("/terrain/{tid}/parcel/{pid}", terrainId, parcelId))
                .andExpect(status().isNoContent());
    }
}
