package com.agro.iotservice.controller;

import com.agro.iotservice.constants.AlertKind;
import com.agro.iotservice.constants.AlertState;
import com.agro.iotservice.exception.AlertNotFoundException;
import com.agro.iotservice.exception.GlobalExceptionHandler;
import com.agro.iotservice.model.SensorAlert;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.service.AlertService;
import com.agro.iotservice.service.I18nService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AlertController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class AlertControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AlertService service;
    @MockitoBean I18nService i18n;
    @MockitoBean DeviceApiKeyRepository deviceApiKeyRepository;

    @BeforeEach
    void stubI18n() {
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void list_returns200() throws Exception {
        when(service.search(any(), any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/alert"))
                .andExpect(status().isOk());
    }

    @Test
    void review_passesReviewerFromHeader() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        mockMvc.perform(post("/alert/{id}/review", id)
                        .header("X-User-Id", reviewer.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"manual review\"}"))
                .andExpect(status().isOk());
        verify(service).review(id, reviewer, "manual review");
    }

    @Test
    void resolve_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/alert/{id}/resolve", id))
                .andExpect(status().isOk());
        verify(service).resolve(id);
    }

    @Test
    void missingAlert_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new AlertNotFoundException("alert.not.found"));
        mockMvc.perform(get("/alert/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveMissing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AlertNotFoundException("alert.not.found")).when(service).resolve(id);
        mockMvc.perform(post("/alert/{id}/resolve", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOne_returnsAlertPayload() throws Exception {
        UUID id = UUID.randomUUID();
        SensorAlert alert = new SensorAlert(id, UUID.randomUUID(), UUID.randomUUID(),
                AlertKind.above_max, new BigDecimal("35"),
                Instant.parse("2026-05-11T10:00:00Z"),
                Instant.parse("2026-05-11T10:05:00Z"),
                3, AlertState.new_, null, null, null, null);
        when(service.getById(id)).thenReturn(alert);
        mockMvc.perform(get("/alert/{id}", id))
                .andExpect(status().isOk());
    }
}
