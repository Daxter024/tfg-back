package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentMimeForbiddenException;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.service.AttachmentService;
import com.agro.terrainservice.service.I18nService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AttachmentController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class AttachmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AttachmentService attachmentService;
    @MockitoBean private I18nService i18nService;

    @Test
    void upload_returns201_withDto() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attId = UUID.randomUUID();

        AttachmentDTO dto = new AttachmentDTO(
                attId, terrainId, "img.jpg", "image/jpeg", 3L, userId, Instant.now(),
                "/terrain/" + terrainId + "/attachment/" + attId + "/content"
        );
        when(attachmentService.upload(eq(terrainId), eq(userId), any())).thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(attId.toString()))
                .andExpect(jsonPath("$.mime_type").value("image/jpeg"));
    }

    @Test
    void upload_returns415_whenServiceThrowsMimeForbidden() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.upload(eq(terrainId), eq(userId), any()))
                .thenThrow(new AttachmentMimeForbiddenException("MIME forbidden"));

        MockMultipartFile file = new MockMultipartFile("file", "evil.exe",
                "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void list_returnsArray() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(attachmentService.list(eq(terrainId), eq(userId))).thenReturn(List.of());

        mockMvc.perform(get("/terrain/{terrainId}/attachment", terrainId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void delete_returns204() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
