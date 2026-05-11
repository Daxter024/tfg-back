package com.agro.iotservice.controller;

import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.exception.GlobalExceptionHandler;
import com.agro.iotservice.exception.InvalidThresholdException;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.service.I18nService;
import com.agro.iotservice.service.ThresholdService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ThresholdController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class ThresholdControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ThresholdService service;
    @MockitoBean I18nService i18n;
    @MockitoBean DeviceApiKeyRepository deviceApiKeyRepository;

    @BeforeEach
    void stubI18n() {
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void list_returnsList() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/threshold"))
                .andExpect(status().isOk());
    }

    @Test
    void create_validBody_returns201() throws Exception {
        UUID sensorId = UUID.randomUUID();
        when(service.create(any())).thenReturn(UUID.randomUUID());
        String body = objectMapper.writeValueAsString(Map.of(
                "sensor_id", sensorId,
                "min_value", new BigDecimal("10"),
                "max_value", new BigDecimal("30")));
        mockMvc.perform(post("/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void create_invalidXor_returns400() throws Exception {
        doThrow(new InvalidThresholdException("threshold.target.xor"))
                .when(service).create(any());
        String body = objectMapper.writeValueAsString(Map.of(
                "sensor_id", UUID.randomUUID(),
                "variable", VariableKind.temperature,
                "min_value", new BigDecimal("10")));
        mockMvc.perform(post("/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
