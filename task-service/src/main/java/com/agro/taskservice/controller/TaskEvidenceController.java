package com.agro.taskservice.controller;

import com.agro.taskservice.model.TaskEvidence;
import com.agro.taskservice.service.FileStorageService;
import com.agro.taskservice.service.TaskEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de evidencias de una tarea (HU-TAR-02). Idem a HU-TER-03:
 * {@code POST /task/{id}/evidence} (multipart), {@code GET .../evidence},
 * {@code GET .../evidence/{evidenceId}/content},
 * {@code DELETE .../evidence/{evidenceId}}.
 */
@RestController
@RequestMapping("/task/{taskId}/evidence")
@RequiredArgsConstructor
public class TaskEvidenceController {

    private final TaskEvidenceService evidenceService;
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id") UUID uploadedBy) throws IOException {
        UUID id = evidenceService.upload(taskId, file, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping
    public ResponseEntity<List<TaskEvidence>> list(@PathVariable UUID taskId) {
        return ResponseEntity.ok(evidenceService.listForTask(taskId));
    }

    @GetMapping("/{evidenceId}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable UUID taskId,
            @PathVariable UUID evidenceId) throws IOException {
        TaskEvidence e = evidenceService.load(evidenceId);
        InputStream in = fileStorageService.load(e.storage_key());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.mime_type()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + e.original_name() + "\"")
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID taskId,
            @PathVariable UUID evidenceId) {
        evidenceService.delete(evidenceId);
        return ResponseEntity.noContent().build();
    }
}
