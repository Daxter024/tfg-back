package com.agro.iotservice.controller;

import com.agro.iotservice.exception.GlobalExceptionHandler;
import com.agro.iotservice.ingestor.ReadingIngestor;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.service.I18nService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class IngestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ReadingIngestor ingestor;
    @MockitoBean I18nService i18n;
    // DeviceKeyAuthFilter is a @Component — its dep needs a mock to boot.
    @MockitoBean DeviceApiKeyRepository deviceApiKeyRepository;

    @BeforeEach
    void stubI18n() {
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // DeviceKeyAuthFilter is wired in the @WebMvcTest slice; let it pass.
        when(deviceApiKeyRepository.verifyActiveKey(any(), anyString())).thenReturn(true);
    }

    @Test
    void validBatch_returns201WithInsertedCount() throws Exception {
        UUID sid = UUID.randomUUID();
        when(ingestor.ingest(any(), any())).thenReturn(2);

        String body = objectMapper.writeValueAsString(Map.of(
                "readings", new Object[]{
                        Map.of("recorded_at", Instant.now().minusSeconds(60).toString(),
                                "value", new BigDecimal("21.5")),
                        Map.of("recorded_at", Instant.now().minusSeconds(30).toString(),
                                "value", new BigDecimal("22.0"))
                }));

        mockMvc.perform(post("/ingest/sensor/{id}/reading", sid)
                        .header("X-Device-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(2));
    }

    @Test
    void emptyBatch_returns400() throws Exception {
        UUID sid = UUID.randomUUID();
        mockMvc.perform(post("/ingest/sensor/{id}/reading", sid)
                        .header("X-Device-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readings\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingKey_returns401() throws Exception {
        UUID sid = UUID.randomUUID();
        mockMvc.perform(post("/ingest/sensor/{id}/reading", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readings\":[{\"recorded_at\":\"2026-05-11T10:00:00Z\",\"value\":1.0}]}"))
                .andExpect(status().isUnauthorized());
    }
}
