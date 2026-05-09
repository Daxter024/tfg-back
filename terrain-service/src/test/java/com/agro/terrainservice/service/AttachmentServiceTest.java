package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentMimeForbiddenException;
import com.agro.terrainservice.exception.AttachmentNotFoundException;
import com.agro.terrainservice.exception.AttachmentQuotaExceededException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.model.Attachment;
import com.agro.terrainservice.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttachmentServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private TerrainService terrainService;
    @Mock private I18nService i18nService;

    @InjectMocks
    private AttachmentService attachmentService;

    private final UUID terrainId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(true);
    }

    @Test
    @DisplayName("TER-5.04 - upload rechaza MIME fuera de whitelist")
    void upload_rejectsMimeOutsideWhitelist() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", new byte[]{1, 2, 3});

        AttachmentMimeForbiddenException ex = assertThrows(AttachmentMimeForbiddenException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        assertThat(ex.getMessage()).contains("attachment.mime.forbidden");

        verify(attachmentRepository, never()).insert(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("TER-5.06 - upload rechaza cuando MIME es null (Content-Type ausente)")
    void upload_rejectsWhenMimeIsNull() {
        MockMultipartFile file = new MockMultipartFile("file", "x.bin", null, new byte[]{1, 2, 3});

        AttachmentMimeForbiddenException ex = assertThrows(AttachmentMimeForbiddenException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        assertThat(ex.getMessage()).contains("attachment.mime.forbidden");
    }

    @Test
    @DisplayName("TER-5.07 - upload rechaza cuando size = 0 (size <= 0)")
    void upload_rejectsZeroSize() {
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{});

        assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    @DisplayName("TER-5.10 - upload rechaza fichero > 10 MB con AttachmentQuotaExceeded")
    void upload_rejectsFileLargerThan10Mb() {
        byte[] big = new byte[(int) (AttachmentService.MAX_FILE_SIZE_BYTES + 1)];
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", big);

        assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    @DisplayName("TER-5.11 - upload rechaza cuando cuota acumulada > 100 MB")
    void upload_rejectsWhenTerrainQuotaWouldBeExceeded() {
        // Terreno con 95 MB ya consumidos; nuevo de 10 MB debe rebasar la cuota de 100 MB.
        when(attachmentRepository.sumSizeByTerrainId(terrainId))
                .thenReturn(95L * 1024 * 1024);

        byte[] body = new byte[(int) AttachmentService.MAX_FILE_SIZE_BYTES];
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", body);

        AttachmentQuotaExceededException ex = assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        assertThat(ex.getMessage()).contains("attachment.quota.exceeded");
    }

    @Test
    @DisplayName("TER-5.12 - upload acepta cuando suma cuota = 100 MB exacto")
    void upload_acceptsAtQuotaBoundary() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId))
                .thenReturn(90L * 1024 * 1024);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");

        byte[] body = new byte[(int) AttachmentService.MAX_FILE_SIZE_BYTES];
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", body);

        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), anyString(), anyString(),
                anyLong(), anyString(), eq(userId))).thenReturn(newId);

        Attachment saved = new Attachment(newId, terrainId, "img.jpg", "image/jpeg",
                body.length, "k", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);
        assertThat(dto.id()).isEqualTo(newId);
    }

    @Test
    @DisplayName("TER-5.13 - upload rechaza cuando terreno no existe (existsForUser false)")
    void upload_rejectsWhenTerrainDoesNotBelongToUser() {
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1});

        TerrainNotFoundException ex = assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        assertThat(ex.getMessage()).contains("terrain.notfound");
    }

    @Test
    @DisplayName("TER-5.14 - upload rechaza cuando terreno pertenece a otro usuario")
    void upload_rejectsWhenTerrainBelongsToOtherUser() {
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1});

        assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    @DisplayName("TER-5.01 unit - upload almacena y persiste cuando todos los checks pasan")
    void upload_storesAndPersistsAttachment_whenAllChecksPass() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("key/file");
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), eq("img.jpg"), eq("image/jpeg"),
                eq(3L), eq("key/file"), eq(userId))).thenReturn(newId);

        Attachment saved = new Attachment(newId, terrainId, "img.jpg", "image/jpeg", 3,
                "key/file", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1, 2, 3});

        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);

        assertThat(dto.id()).isEqualTo(newId);
        assertThat(dto.mime_type()).isEqualTo("image/jpeg");
        assertThat(dto.size_bytes()).isEqualTo(3);
        assertThat(dto.download_url()).isEqualTo("/terrain/" + terrainId + "/attachment/" + newId + "/content");
    }

    @Test
    @DisplayName("TER-5.02 - upload PNG valido")
    void upload_acceptsPng() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), anyString(), eq("image/png"),
                anyLong(), anyString(), eq(userId))).thenReturn(newId);
        Attachment saved = new Attachment(newId, terrainId, "img.png", "image/png", 2,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[]{1, 2});
        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);
        assertThat(dto.mime_type()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("TER-5.03 - upload PDF valido")
    void upload_acceptsPdf() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), anyString(), eq("application/pdf"),
                anyLong(), anyString(), eq(userId))).thenReturn(newId);
        Attachment saved = new Attachment(newId, terrainId, "doc.pdf", "application/pdf", 4,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", new byte[]{1, 2, 3, 4});
        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);
        assertThat(dto.mime_type()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("TER-5.08 - upload acepta size = 1 byte")
    void upload_acceptsOneByte() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), anyString(), eq("image/jpeg"),
                anyLong(), anyString(), eq(userId))).thenReturn(newId);
        Attachment saved = new Attachment(newId, terrainId, "img.jpg", "image/jpeg", 1,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1});
        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);
        assertThat(dto.size_bytes()).isEqualTo(1);
    }

    @Test
    @DisplayName("TER-5.09 - upload acepta size = 10 MB exacto")
    void upload_acceptsExact10Mb() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.insert(eq(terrainId), anyString(), anyString(),
                anyLong(), anyString(), eq(userId))).thenReturn(newId);
        byte[] body = new byte[(int) AttachmentService.MAX_FILE_SIZE_BYTES];
        Attachment saved = new Attachment(newId, terrainId, "img.jpg", "image/jpeg",
                body.length, "k", userId, Instant.now());
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(saved));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", body);
        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);
        assertThat(dto.size_bytes()).isEqualTo(body.length);
    }

    @Test
    @DisplayName("TER-5.18 - upload propaga IOException del storage como RuntimeException")
    void upload_propagatesIoExceptionAsRuntimeException() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new IOException("disk full"));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1, 2});

        assertThrows(RuntimeException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    @DisplayName("TER-5.19 - upload no inserta fila si storage falla (rollback transaccional)")
    void upload_doesNotInsert_whenStorageFails() throws Exception {
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        when(fileStorageService.store(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new IOException("disk full"));

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1, 2});

        assertThrows(RuntimeException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        verify(attachmentRepository, never()).insert(any(), any(), any(), anyLong(), any(), any());
    }

    // -------- Section 7: GET /attachment/{id}/content --------

    @Test
    @DisplayName("TER-7.04 - download lanza AttachmentNotFound cuando id no existe")
    void download_returns404_whenAttachmentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        AttachmentNotFoundException ex = assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.download(terrainId, id));
        assertThat(ex.getMessage()).contains("attachment.not.found");
    }

    @Test
    @DisplayName("TER-7.05 - download lanza AttachmentNotFound cuando adjunto pertenece a otro terreno")
    void download_returns404_whenAttachmentBelongsToAnotherTerrain() {
        UUID id = UUID.randomUUID();
        Attachment other = new Attachment(id, UUID.randomUUID(), "img.jpg", "image/jpeg", 1,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(other));

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.download(terrainId, id));
    }

    @Test
    @DisplayName("TER-7.06 - download lanza AttachmentNotFound cuando fichero perdido (IOException)")
    void download_returns404_whenStorageFails() throws IOException {
        UUID id = UUID.randomUUID();
        Attachment a = new Attachment(id, terrainId, "img.jpg", "image/jpeg", 1,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(a));
        when(fileStorageService.load("k")).thenThrow(new IOException("file gone"));

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.download(terrainId, id));
    }

    @Test
    @DisplayName("TER-7.01 unit - download devuelve resource con stream y attachment")
    void download_returnsResource_whenAllChecksPass() throws IOException {
        UUID id = UUID.randomUUID();
        Attachment a = new Attachment(id, terrainId, "img.jpg", "image/jpeg", 4,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(a));
        InputStream is = new ByteArrayInputStream(new byte[]{1, 2, 3, 4});
        when(fileStorageService.load("k")).thenReturn(is);

        AttachmentService.AttachmentResource res = attachmentService.download(terrainId, id);

        assertThat(res.attachment()).isEqualTo(a);
        assertThat(res.stream()).isEqualTo(is);
    }

    // -------- Section 6: list --------

    @Test
    @DisplayName("TER-6.02 unit - list devuelve todos los adjuntos para el dueno")
    void list_returnsAllForOwner() {
        Attachment a = new Attachment(UUID.randomUUID(), terrainId, "a.png", "image/png", 1,
                "ka", userId, Instant.now());
        when(attachmentRepository.findByTerrainId(terrainId)).thenReturn(List.of(a));

        List<AttachmentDTO> dtos = attachmentService.list(terrainId, userId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(a.id());
    }

    @Test
    @DisplayName("TER-6.03 unit - list lanza TerrainNotFound cuando terreno no es del usuario")
    void list_throwsTerrainNotFound_whenOwnerMismatch() {
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(false);

        assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.list(terrainId, userId));
    }

    @Test
    @DisplayName("TER-6.06 unit - download_url construida correctamente")
    void list_downloadUrlsArePopulated() {
        UUID id = UUID.randomUUID();
        Attachment a = new Attachment(id, terrainId, "a.png", "image/png", 1,
                "ka", userId, Instant.now());
        when(attachmentRepository.findByTerrainId(terrainId)).thenReturn(List.of(a));

        List<AttachmentDTO> dtos = attachmentService.list(terrainId, userId);

        assertThat(dtos.get(0).download_url())
                .isEqualTo("/terrain/" + terrainId + "/attachment/" + id + "/content");
    }

    // -------- Section 8: delete --------

    @Test
    @DisplayName("TER-8.01 - delete borra fila y storage")
    void delete_removesRowAndStorage() {
        UUID id = UUID.randomUUID();
        Attachment a = new Attachment(id, terrainId, "img.jpg", "image/jpeg", 1, "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(a));

        attachmentService.delete(terrainId, id, userId);

        verify(attachmentRepository).deleteById(id);
        verify(fileStorageService).delete("k");
    }

    @Test
    @DisplayName("TER-8.02 - delete lanza AttachmentNotFound cuando id no existe")
    void delete_throwsAttachmentNotFound_whenIdMissing() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.delete(terrainId, id, userId));
    }

    @Test
    @DisplayName("TER-8.03 - delete lanza AttachmentNotFound cuando ownership mismatch")
    void delete_throwsAttachmentNotFound_whenOwnershipMismatch() {
        UUID id = UUID.randomUUID();
        Attachment other = new Attachment(id, UUID.randomUUID(), "img.jpg", "image/jpeg", 1,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(other));

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.delete(terrainId, id, userId));
    }

    @Test
    @DisplayName("TER-8.04 - delete lanza TerrainNotFound cuando terreno de otro usuario")
    void delete_throwsTerrainNotFound_whenOwnerMismatch() {
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(false);
        UUID id = UUID.randomUUID();

        assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.delete(terrainId, id, userId));
    }
}
