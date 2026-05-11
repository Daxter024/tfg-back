package com.agro.inputservice.controller;

import com.agro.inputservice.dto.PageResponse;
import com.agro.inputservice.exception.GlobalExceptionHandler;
import com.agro.inputservice.exception.InputNotFoundException;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import com.agro.inputservice.service.I18nService;
import com.agro.inputservice.service.InputService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InputController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class InputControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean InputService inputService;
    @MockitoBean I18nService i18nService;

    @BeforeEach
    void stubI18n() {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void list_returns_200_with_empty_page() throws Exception {
        when(inputService.search(eq(null), eq(null), eq(false), eq(false), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(0, 20, 0, List.of()));
        mockMvc.perform(get("/input"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getOne_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        Input in = new Input(id, "Urea", InputCategory.fertilizante, "kg",
                null, null, null, UUID.randomUUID(), Instant.now(), null, null, BigDecimal.TEN);
        when(inputService.getById(id)).thenReturn(in);
        mockMvc.perform(get("/input/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Urea"))
                .andExpect(jsonPath("$.current_stock").value(10));
    }

    @Test
    void getOne_returns_404_when_missing() throws Exception {
        UUID id = UUID.randomUUID();
        when(inputService.getById(id)).thenThrow(new InputNotFoundException("nope"));
        mockMvc.perform(get("/input/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns_201() throws Exception {
        UUID newId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(inputService.create(any(), eq(userId))).thenReturn(newId);

        Map<String, Object> body = Map.of(
                "name", "Glifosato",
                "category", "fitosanitario",
                "unit", "L",
                "low_stock_threshold", 5,
                "supplier", "Bayer"
        );
        mockMvc.perform(post("/input")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newId.toString()));
    }

    @Test
    void create_returns_400_on_validation_error() throws Exception {
        UUID userId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "name", "",   // blank
                "category", "fertilizante",
                "unit", "kg"
        );
        mockMvc.perform(post("/input")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = Map.of("name", "Renombrado");
        mockMvc.perform(patch("/input/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns_204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/input/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns_404_when_missing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InputNotFoundException("missing")).when(inputService).softDelete(id);
        mockMvc.perform(delete("/input/{id}", id))
                .andExpect(status().isNotFound());
    }
}
