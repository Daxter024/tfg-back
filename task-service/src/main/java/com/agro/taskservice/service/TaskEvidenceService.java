package com.agro.taskservice.service;

import com.agro.taskservice.exception.InvalidFieldException;
import com.agro.taskservice.exception.TaskNotFoundException;
import com.agro.taskservice.model.TaskEvidence;
import com.agro.taskservice.repository.TaskEvidenceRepository;
import com.agro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HU-TAR-02 — evidencias. Reglas identicas a las de terrain-service HU-TER-03:
 * MIME en {(image/jpeg, image/png, application/pdf)}, tamano maximo 10 MB,
 * almacenamiento via {@link FileStorageService} (filesystem por defecto).
 */
@Service
@RequiredArgsConstructor
public class TaskEvidenceService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    /** 10 MB en bytes (mismo limite que HU-TER-03). */
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final TaskRepository taskRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final FileStorageService fileStorageService;
    private final I18nService i18nService;

    @Transactional
    public UUID upload(UUID taskId, MultipartFile file, UUID uploadedBy) throws IOException {
        if (taskRepository.findById(taskId).isEmpty()) {
            throw new TaskNotFoundException(i18nService.getMessage("task.not.found", taskId));
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new InvalidFieldException(i18nService.getMessage("task.evidence.mime.invalid"));
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidFieldException(i18nService.getMessage("task.evidence.size.exceeded"));
        }
        String storageKey;
        try (var in = file.getInputStream()) {
            storageKey = fileStorageService.store(in, file.getSize(),
                    "task-" + taskId, file.getOriginalFilename());
        }
        return evidenceRepository.insert(taskId, file.getOriginalFilename(),
                mime, file.getSize(), storageKey, uploadedBy);
    }

    @Transactional(readOnly = true)
    public List<TaskEvidence> listForTask(UUID taskId) {
        return evidenceRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public TaskEvidence load(UUID evidenceId) {
        return evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new TaskNotFoundException(
                        i18nService.getMessage("task.evidence.not.found", evidenceId)));
    }

    @Transactional
    public void delete(UUID evidenceId) {
        TaskEvidence e = evidenceRepository.findById(evidenceId).orElseThrow(() ->
                new TaskNotFoundException(i18nService.getMessage("task.evidence.not.found", evidenceId)));
        evidenceRepository.delete(evidenceId);
        fileStorageService.delete(e.storage_key());
    }
}
