package com.agro.iotservice.controller;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.exception.GlobalExceptionHandler;
import com.agro.iotservice.exception.SensorNotFoundException;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.repository.SensorReadingRepository;
import com.agro.iotservice.service.I18nService;
import com.agro.iotservice.service.SensorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReadingController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class ReadingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SensorService sensorService;
    @MockitoBean SensorReadingRepository repo;
    @MockitoBean I18nService i18n;
    // DeviceKeyAuthFilter is a @Component picked up by @WebMvcTest; satisfy
    // its repository dependency with a mock so the context can boot.
    @MockitoBean DeviceApiKeyRepository deviceApiKeyRepository;

    private static final UUID SID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void stub() {
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(sensorService.getById(SID)).thenReturn(
                new Sensor(SID, null, VariableKind.temperature, "C",
                        UUID.randomUUID(), 300, SensorStatus.active,
                        UUID.randomUUID(), Instant.now(), null, null, null));
    }

    @Test
    void rangeUnder1h_defaultsToRaw() throws Exception {
        when(repo.findRaw(eq(SID), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/sensor/{id}/reading", SID)
                        .param("from", "2026-05-11T08:00:00Z")
                        .param("to",   "2026-05-11T08:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agg").value("raw"));
        verify(repo).findRaw(eq(SID), any(), any());
    }

    @Test
    void rangeUnder24h_defaultsToHourly() throws Exception {
        when(repo.findHourly(eq(SID), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/sensor/{id}/reading", SID)
                        .param("from", "2026-05-11T00:00:00Z")
                        .param("to",   "2026-05-11T18:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agg").value("hourly"));
        verify(repo).findHourly(eq(SID), any(), any());
    }

    @Test
    void rangeOver31d_defaultsToDailyWithDownsampledHeader() throws Exception {
        when(repo.findDaily(eq(SID), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/sensor/{id}/reading", SID)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to",   "2026-03-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Downsampled", "true"))
                .andExpect(jsonPath("$.agg").value("daily"));
    }

    @Test
    void rangeOver365d_throws400() throws Exception {
        mockMvc.perform(get("/sensor/{id}/reading", SID)
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to",   "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repo);
    }

    @Test
    void sensorMissing_propagates404() throws Exception {
        UUID other = UUID.randomUUID();
        when(sensorService.getById(other)).thenThrow(new SensorNotFoundException("not found"));
        mockMvc.perform(get("/sensor/{id}/reading", other)
                        .param("from", "2026-05-11T08:00:00Z")
                        .param("to",   "2026-05-11T08:30:00Z"))
                .andExpect(status().isNotFound());
    }
}
