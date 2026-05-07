package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentMimeForbiddenException;
import com.agro.terrainservice.exception.AttachmentNotFoundException;
import com.agro.terrainservice.exception.AttachmentQuotaExceededException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.model.Attachment;
import com.agro.terrainservice.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

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
    void upload_rejectsMimeOutsideWhitelist() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", new byte[]{1, 2, 3});

        assertThrows(AttachmentMimeForbiddenException.class,
                () -> attachmentService.upload(terrainId, userId, file));

        verify(attachmentRepository, never()).insert(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void upload_rejectsFileLargerThan10Mb() {
        byte[] big = new byte[(int) (AttachmentService.MAX_FILE_SIZE_BYTES + 1)];
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", big);

        assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    void upload_rejectsWhenTerrainQuotaWouldBeExceeded() {
        // Terreno con 95 MB ya consumidos; nuevo de 10 MB debe rebasar la cuota de 100 MB.
        when(attachmentRepository.sumSizeByTerrainId(terrainId))
                .thenReturn(95L * 1024 * 1024);

        byte[] body = new byte[(int) AttachmentService.MAX_FILE_SIZE_BYTES];
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", body);

        assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    void upload_rejectsWhenTerrainDoesNotBelongToUser() {
        when(terrainService.existsForUser(eq(terrainId), eq(userId))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[]{1});

        assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
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
    void download_returns404_whenAttachmentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.download(terrainId, id));
    }

    @Test
    void download_returns404_whenAttachmentBelongsToAnotherTerrain() {
        UUID id = UUID.randomUUID();
        Attachment other = new Attachment(id, UUID.randomUUID(), "img.jpg", "image/jpeg", 1,
                "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(other));

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.download(terrainId, id));
    }

    @Test
    void list_returnsAllForOwner() {
        Attachment a = new Attachment(UUID.randomUUID(), terrainId, "a.png", "image/png", 1,
                "ka", userId, Instant.now());
        when(attachmentRepository.findByTerrainId(terrainId)).thenReturn(List.of(a));

        List<AttachmentDTO> dtos = attachmentService.list(terrainId, userId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(a.id());
    }

    @Test
    void delete_removesRowAndStorage() {
        UUID id = UUID.randomUUID();
        Attachment a = new Attachment(id, terrainId, "img.jpg", "image/jpeg", 1, "k", userId, Instant.now());
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(a));

        attachmentService.delete(terrainId, id, userId);

        verify(attachmentRepository).deleteById(id);
        verify(fileStorageService).delete("k");
    }
}
