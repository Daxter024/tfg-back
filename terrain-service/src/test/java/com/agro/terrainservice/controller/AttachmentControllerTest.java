package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentMimeForbiddenException;
import com.agro.terrainservice.exception.AttachmentNotFoundException;
import com.agro.terrainservice.exception.AttachmentQuotaExceededException;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.model.Attachment;
import com.agro.terrainservice.service.AttachmentService;
import com.agro.terrainservice.service.I18nService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("TER-5.01 - Subida JPG valida devuelve 201 con AttachmentDTO")
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
                .andExpect(jsonPath("$.mime_type").value("image/jpeg"))
                .andExpect(jsonPath("$.size_bytes").value(3))
                .andExpect(jsonPath("$.download_url")
                        .value("/terrain/" + terrainId + "/attachment/" + attId + "/content"));
    }

    @Test
    @DisplayName("TER-5.04 - MIME no permitido (text/plain) devuelve 415")
    void upload_returns415_whenServiceThrowsMimeForbidden() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.upload(eq(terrainId), eq(userId), any()))
                .thenThrow(new AttachmentMimeForbiddenException("attachment.mime.forbidden"));

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt",
                "text/plain", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Attachment MIME type not allowed"))
                .andExpect(jsonPath("$.detail").value(containsString("attachment.mime.forbidden")));
    }

    @Test
    @DisplayName("TER-5.05 - MIME no permitido (application/zip) devuelve 415")
    void upload_returns415_whenZipMimeRejected() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.upload(eq(terrainId), eq(userId), any()))
                .thenThrow(new AttachmentMimeForbiddenException("attachment.mime.forbidden"));

        MockMultipartFile file = new MockMultipartFile("file", "x.zip",
                "application/zip", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Attachment MIME type not allowed"));
    }

    @Test
    @DisplayName("TER-5.11 - Cuota acumulada superada devuelve 400")
    void upload_returns400_whenQuotaExceeded() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.upload(eq(terrainId), eq(userId), any()))
                .thenThrow(new AttachmentQuotaExceededException("attachment.quota.exceeded"));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg",
                "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Attachment quota exceeded"))
                .andExpect(jsonPath("$.detail").value(containsString("attachment.quota.exceeded")));
    }

    @Test
    @DisplayName("TER-5.13 - Terreno inexistente al subir devuelve 404")
    void upload_returns404_whenTerrainMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.upload(eq(terrainId), eq(userId), any()))
                .thenThrow(new TerrainNotFoundException("terrain.notfound"));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg",
                "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"));
    }

    @Test
    @DisplayName("TER-5.15 - Sin parametro user_id dispara 400")
    void upload_returns400_whenUserIdMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg",
                "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-5.16 - Sin parte file dispara 400 Multipart request error")
    void upload_returns400_whenFilePartMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(multipart("/terrain/{terrainId}/attachment", terrainId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isBadRequest());
    }

    // -------- Section 6: GET /terrain/{id}/attachment --------

    @Test
    @DisplayName("TER-6.01 - Listar terreno sin adjuntos devuelve []")
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
    @DisplayName("TER-6.02 - Listar terreno con N adjuntos")
    void list_returnsAttachments() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();

        AttachmentDTO d1 = new AttachmentDTO(a1, terrainId, "a.png", "image/png", 1L, userId,
                Instant.now(), "/terrain/" + terrainId + "/attachment/" + a1 + "/content");
        AttachmentDTO d2 = new AttachmentDTO(a2, terrainId, "b.pdf", "application/pdf", 2L, userId,
                Instant.now(), "/terrain/" + terrainId + "/attachment/" + a2 + "/content");
        when(attachmentService.list(eq(terrainId), eq(userId))).thenReturn(List.of(d1, d2));

        mockMvc.perform(get("/terrain/{terrainId}/attachment", terrainId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].terrain_id").value(terrainId.toString()))
                .andExpect(jsonPath("$[1].terrain_id").value(terrainId.toString()));
    }

    @Test
    @DisplayName("TER-6.03 - Listar terreno inexistente devuelve 404")
    void list_returns404_whenTerrainMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.list(eq(terrainId), eq(userId)))
                .thenThrow(new TerrainNotFoundException("terrain.notfound"));

        mockMvc.perform(get("/terrain/{terrainId}/attachment", terrainId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"));
    }

    @Test
    @DisplayName("TER-6.05 - Listar sin user_id dispara 400")
    void list_returns400_whenUserIdMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        mockMvc.perform(get("/terrain/{terrainId}/attachment", terrainId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-6.06 - download_url correcta en cada DTO")
    void list_attachmentsHaveCorrectDownloadUrl() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attId = UUID.randomUUID();
        AttachmentDTO dto = new AttachmentDTO(attId, terrainId, "a.png", "image/png", 1L, userId,
                Instant.now(), "/terrain/" + terrainId + "/attachment/" + attId + "/content");
        when(attachmentService.list(eq(terrainId), eq(userId))).thenReturn(List.of(dto));

        mockMvc.perform(get("/terrain/{terrainId}/attachment", terrainId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].download_url")
                        .value("/terrain/" + terrainId + "/attachment/" + attId + "/content"));
    }

    // -------- Section 7: GET /attachment/{id}/content --------

    @Test
    @DisplayName("TER-7.01 - Descarga JPG existente devuelve 200 con Content-Type/Disposition")
    void download_returns200_withContentTypeAndDisposition() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] data = new byte[]{1, 2, 3, 4};

        Attachment a = new Attachment(attachmentId, terrainId, "img.jpg", "image/jpeg",
                data.length, "k/img.jpg", userId, Instant.now());
        AttachmentService.AttachmentResource res =
                new AttachmentService.AttachmentResource(a, new ByteArrayInputStream(data));
        when(attachmentService.download(eq(terrainId), eq(attachmentId))).thenReturn(res);

        mockMvc.perform(get("/terrain/{tid}/attachment/{aid}/content", terrainId, attachmentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Content-Length", String.valueOf(data.length)))
                .andExpect(header().string("Content-Disposition", containsString("img.jpg")));
    }

    @Test
    @DisplayName("TER-7.04 - ID inexistente al descargar devuelve 404")
    void download_returns404_whenAttachmentMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(attachmentService.download(eq(terrainId), eq(attachmentId)))
                .thenThrow(new AttachmentNotFoundException("attachment.not.found"));

        mockMvc.perform(get("/terrain/{tid}/attachment/{aid}/content", terrainId, attachmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Attachment not found"))
                .andExpect(jsonPath("$.detail").value(containsString("attachment.not.found")));
    }

    @Test
    @DisplayName("TER-7.07 - UUID malformado en path dispara 400")
    void download_returns400_whenUuidMalformed() throws Exception {
        UUID terrainId = UUID.randomUUID();
        mockMvc.perform(get("/terrain/{tid}/attachment/{aid}/content", terrainId, "abc"))
                .andExpect(status().isBadRequest());
    }

    // -------- Section 8: DELETE /attachment/{id} --------

    @Test
    @DisplayName("TER-8.01 - Borrado del propietario devuelve 204")
    void delete_returns204() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        doNothing().when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));

        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TER-8.02 - Borrado de id inexistente devuelve 404")
    void delete_returns404_whenAttachmentMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new AttachmentNotFoundException("attachment.not.found"))
                .when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));

        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Attachment not found"));
    }

    @Test
    @DisplayName("TER-8.03 - Adjunto de otro terreno (mismatch) devuelve 404")
    void delete_returns404_whenOwnershipMismatch() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new AttachmentNotFoundException("attachment.not.found"))
                .when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));

        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TER-8.04 - Terreno de otro usuario devuelve 404 Terrain not found")
    void delete_returns404_whenWrongUser() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new TerrainNotFoundException("terrain.notfound"))
                .when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));

        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"));
    }

    @Test
    @DisplayName("TER-8.05 - Borrado sin user_id dispara 400")
    void delete_returns400_whenUserIdMissing() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-8.06 - Idempotencia: primer DELETE 204, segundo 404")
    void delete_idempotency() throws Exception {
        UUID terrainId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Primer DELETE
        doNothing().when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));
        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNoContent());

        // Segundo DELETE: 404
        doThrow(new AttachmentNotFoundException("attachment.not.found"))
                .when(attachmentService).delete(eq(terrainId), eq(attachmentId), eq(userId));
        mockMvc.perform(delete("/terrain/{terrainId}/attachment/{id}", terrainId, attachmentId)
                        .param("user_id", userId.toString()))
                .andExpect(status().isNotFound());
    }
}
