package com.agro.terrainservice.service;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.exception.AttachmentMimeForbiddenException;
import com.agro.terrainservice.exception.AttachmentNotFoundException;
import com.agro.terrainservice.exception.AttachmentQuotaExceededException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.model.Attachment;
import com.agro.terrainservice.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de adjuntos (HU-TER-03). Aplica la whitelist de MIME, el limite por
 * archivo (10 MB) y la cuota acumulada por terreno (100 MB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;       // 10 MB
    public static final long MAX_TERRAIN_QUOTA_BYTES = 100L * 1024 * 1024;  // 100 MB

    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final TerrainService terrainService;
    private final I18nService i18nService;

    @Transactional
    public AttachmentDTO upload(UUID terrainId, UUID userId, MultipartFile file) {
        if (!terrainService.existsForUser(terrainId, userId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }

        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime.toLowerCase())) {
            throw new AttachmentMimeForbiddenException(
                    i18nService.getMessage("attachment.mime.forbidden", mime == null ? "unknown" : mime)
            );
        }

        long size = file.getSize();
        if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
            throw new AttachmentQuotaExceededException(
                    i18nService.getMessage("attachment.size.exceeded")
            );
        }

        long currentTotal = attachmentRepository.sumSizeByTerrainId(terrainId);
        if (currentTotal + size > MAX_TERRAIN_QUOTA_BYTES) {
            throw new AttachmentQuotaExceededException(
                    i18nService.getMessage("attachment.quota.exceeded", terrainId)
            );
        }

        String storageKey;
        try (InputStream in = file.getInputStream()) {
            storageKey = fileStorageService.store(in, size, terrainId.toString(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to store attachment for terrain {}", terrainId, e);
            throw new RuntimeException("Failed to store attachment", e);
        }

        UUID id = attachmentRepository.insert(
                terrainId,
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                mime,
                size,
                storageKey,
                userId
        );

        Attachment saved = attachmentRepository.findById(id).orElseThrow(() ->
                new IllegalStateException("Attachment was inserted but cannot be retrieved: " + id));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDTO> list(UUID terrainId, UUID userId) {
        if (!terrainService.existsForUser(terrainId, userId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }
        return attachmentRepository.findByTerrainId(terrainId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AttachmentResource download(UUID terrainId, UUID attachmentId) {
        Attachment a = attachmentRepository.findById(attachmentId).orElseThrow(() ->
                new AttachmentNotFoundException(
                        i18nService.getMessage("attachment.not.found", attachmentId)
                ));
        if (!a.terrain_id().equals(terrainId)) {
            throw new AttachmentNotFoundException(
                    i18nService.getMessage("attachment.not.found", attachmentId)
            );
        }
        try {
            InputStream stream = fileStorageService.load(a.storage_key());
            return new AttachmentResource(a, stream);
        } catch (IOException e) {
            throw new AttachmentNotFoundException(
                    i18nService.getMessage("attachment.not.found", attachmentId)
            );
        }
    }

    @Transactional
    public void delete(UUID terrainId, UUID attachmentId, UUID userId) {
        if (!terrainService.existsForUser(terrainId, userId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }
        Attachment a = attachmentRepository.findById(attachmentId).orElseThrow(() ->
                new AttachmentNotFoundException(
                        i18nService.getMessage("attachment.not.found", attachmentId)
                ));
        if (!a.terrain_id().equals(terrainId)) {
            throw new AttachmentNotFoundException(
                    i18nService.getMessage("attachment.not.found", attachmentId)
            );
        }
        attachmentRepository.deleteById(attachmentId);
        fileStorageService.delete(a.storage_key());
    }

    private AttachmentDTO toDto(Attachment a) {
        String url = "/terrain/" + a.terrain_id() + "/attachment/" + a.id() + "/content";
        return new AttachmentDTO(
                a.id(),
                a.terrain_id(),
                a.original_name(),
                a.mime_type(),
                a.size_bytes(),
                a.uploaded_by(),
                a.uploaded_at(),
                url
        );
    }

    /** Wrapper para devolver al controller los metadatos + el stream de descarga. */
    public record AttachmentResource(Attachment attachment, InputStream stream) {
    }
}
