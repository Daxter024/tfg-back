package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentNotFoundException;
import com.agro.terrainservice.exception.AttachmentQuotaExceededException;
import com.agro.terrainservice.exception.PayloadTooLargeException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.exception.UnsupportedMediaTypeException;
import com.agro.terrainservice.model.Attachment;
import com.agro.terrainservice.repository.AttachmentRepository;
import com.agro.terrainservice.repository.TerrainRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private TerrainRepository terrainRepository;

    @Mock
    private FileStorageService storage;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private AttachmentService attachmentService;

    private final UUID terrainId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(i18nService.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(any(String.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainRepository.existsById(terrainId)).thenReturn(true);
    }

    @Test
    void upload_ShouldReject_WhenMimeForbidden() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1, 2, 3}
        );

        assertThrows(UnsupportedMediaTypeException.class,
                () -> attachmentService.upload(terrainId, userId, file));
        verify(attachmentRepository, never()).save(any(), any(), any(), anyLongAny(), any(), any());
    }

    @Test
    void upload_ShouldReject_WhenFileTooBig() {
        byte[] payload = new byte[(int) (10L * 1024 * 1024 + 1)];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", payload
        );

        assertThrows(PayloadTooLargeException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    void upload_ShouldReject_WhenQuotaExceeded() {
        byte[] payload = new byte[1024]; // 1 KB
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", payload
        );
        // ya hay 100 MB usados
        when(attachmentRepository.sumSizeByTerrainId(terrainId))
                .thenReturn(100L * 1024 * 1024);

        assertThrows(AttachmentQuotaExceededException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    void upload_ShouldReject_WhenTerrainDoesNotExist() {
        when(terrainRepository.existsById(terrainId)).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.png", "image/png", new byte[]{1}
        );

        assertThrows(TerrainNotFoundException.class,
                () -> attachmentService.upload(terrainId, userId, file));
    }

    @Test
    void upload_ShouldPersistAndReturnDTO_WhenValid() throws Exception {
        byte[] payload = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", payload
        );
        when(attachmentRepository.sumSizeByTerrainId(terrainId)).thenReturn(0L);
        UUID newId = UUID.randomUUID();
        when(attachmentRepository.save(any(), any(), any(), anyLongAny(), any(), any()))
                .thenReturn(newId);
        Attachment stored = new Attachment(
                newId, terrainId, "photo.jpg", "image/jpeg",
                payload.length, "key", userId, Instant.now()
        );
        when(attachmentRepository.findById(newId)).thenReturn(Optional.of(stored));

        AttachmentDTO dto = attachmentService.upload(terrainId, userId, file);

        assertNotNull(dto);
        assertEquals(newId, dto.id());
        assertEquals("image/jpeg", dto.mime_type());
        assertEquals("photo.jpg", dto.original_name());
        verify(storage).store(any(String.class), any(), org.mockito.ArgumentMatchers.eq((long) payload.length));
    }

    @Test
    void delete_ShouldRemoveFromDbAndStorage() throws Exception {
        UUID attId = UUID.randomUUID();
        Attachment att = new Attachment(
                attId, terrainId, "f.pdf", "application/pdf",
                10, "k", userId, Instant.now()
        );
        when(attachmentRepository.findById(attId)).thenReturn(Optional.of(att));
        when(attachmentRepository.deleteById(attId)).thenReturn(1);

        attachmentService.delete(terrainId, attId);

        verify(attachmentRepository).deleteById(attId);
        verify(storage).delete("k");
    }

    @Test
    void delete_ShouldThrow_WhenAttachmentDoesNotBelongToTerrain() {
        UUID attId = UUID.randomUUID();
        UUID otherTerrain = UUID.randomUUID();
        Attachment att = new Attachment(
                attId, otherTerrain, "f.pdf", "application/pdf",
                10, "k", userId, Instant.now()
        );
        when(attachmentRepository.findById(attId)).thenReturn(Optional.of(att));

        assertThrows(AttachmentNotFoundException.class,
                () -> attachmentService.delete(terrainId, attId));
    }

    /** Convenience helper para evitar advertencias de tipos al matchear long primitivos. */
    private static long anyLongAny() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
