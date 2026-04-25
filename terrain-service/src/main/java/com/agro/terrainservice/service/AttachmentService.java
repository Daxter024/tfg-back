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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    /** Whitelist de tipos MIME admitidos (HU-TER-03). */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    /** 10 MB por archivo. */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    /** 100 MB acumulados por terreno. */
    private static final long MAX_TERRAIN_QUOTA = 100L * 1024 * 1024;

    private final AttachmentRepository attachmentRepository;
    private final TerrainRepository terrainRepository;
    private final FileStorageService storage;
    private final I18nService i18nService;

    @Transactional
    public AttachmentDTO upload(UUID terrainId, UUID uploadedBy, MultipartFile file) {
        if (!terrainRepository.existsById(terrainId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedMediaTypeException(
                    i18nService.getMessage("attachment.mime.forbidden")
            );
        }

        long size = file.getSize();
        if (size > MAX_FILE_SIZE) {
            throw new PayloadTooLargeException(
                    i18nService.getMessage("attachment.size.exceeded")
            );
        }

        long currentTotal = attachmentRepository.sumSizeByTerrainId(terrainId);
        if (currentTotal + size > MAX_TERRAIN_QUOTA) {
            throw new AttachmentQuotaExceededException(
                    i18nService.getMessage("attachment.quota.exceeded")
            );
        }

        String storageKey = "%s/%s-%s".formatted(
                terrainId,
                UUID.randomUUID(),
                sanitize(file.getOriginalFilename())
        );

        try (InputStream is = file.getInputStream()) {
            storage.store(storageKey, is, size);
        } catch (IOException ioe) {
            throw new RuntimeException(i18nService.getMessage("attachment.storage.error"), ioe);
        }

        UUID id = attachmentRepository.save(
                terrainId,
                file.getOriginalFilename(),
                mimeType,
                size,
                storageKey,
                uploadedBy
        );

        Attachment saved = attachmentRepository.findById(id).orElseThrow();
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDTO> listByTerrain(UUID terrainId) {
        if (!terrainRepository.existsById(terrainId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }
        return attachmentRepository.findByTerrainId(terrainId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentBinary download(UUID terrainId, UUID attachmentId) {
        Attachment att = attachmentRepository.findById(attachmentId)
                .filter(a -> a.terrain_id().equals(terrainId))
                .orElseThrow(() -> new AttachmentNotFoundException(
                        i18nService.getMessage("attachment.not.found", attachmentId)
                ));
        try {
            InputStream is = storage.load(att.storage_key());
            return new AttachmentBinary(att.mime_type(), att.original_name(),
                    att.size_bytes(), new InputStreamResource(is));
        } catch (IOException ioe) {
            throw new RuntimeException(i18nService.getMessage("attachment.storage.error"), ioe);
        }
    }

    @Transactional
    public void delete(UUID terrainId, UUID attachmentId) {
        Attachment att = attachmentRepository.findById(attachmentId)
                .filter(a -> a.terrain_id().equals(terrainId))
                .orElseThrow(() -> new AttachmentNotFoundException(
                        i18nService.getMessage("attachment.not.found", attachmentId)
                ));
        int rows = attachmentRepository.deleteById(attachmentId);
        if (rows == 0) {
            throw new AttachmentNotFoundException(
                    i18nService.getMessage("attachment.not.found", attachmentId)
            );
        }
        try {
            storage.delete(att.storage_key());
        } catch (IOException ioe) {
            // No revertimos la transacción: el ficheo en disco huérfano se puede limpiar
            // con un job de mantenimiento. Loggeamos para visibilidad.
            log.warn("Could not remove storage object {} for attachment {}: {}",
                    att.storage_key(), attachmentId, ioe.getMessage());
        }
    }

    private AttachmentDTO toDTO(Attachment a) {
        String url = "/terrain/%s/attachment/%s/content".formatted(
                a.terrain_id(), a.id()
        );
        return new AttachmentDTO(
                a.id(), a.terrain_id(), a.original_name(), a.mime_type(),
                a.size_bytes(), a.uploaded_by(), a.uploaded_at(), url
        );
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        // Reemplaza separadores y otros caracteres peligrosos para nombres de archivo.
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Tupla con el binario y sus cabeceras necesarias para la respuesta HTTP. */
    public record AttachmentBinary(
            String mimeType,
            String originalName,
            long sizeBytes,
            InputStreamResource resource
    ) {
    }
}
