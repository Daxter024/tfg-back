package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/terrain/{terrainId}/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> upload(
            @PathVariable UUID terrainId,
            @RequestParam UUID user_id,
            @RequestPart("file") MultipartFile file
    ) {
        AttachmentDTO created = attachmentService.upload(terrainId, user_id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AttachmentDTO>> list(
            @PathVariable UUID terrainId,
            @RequestParam UUID user_id
    ) {
        return ResponseEntity.ok(attachmentService.listByTerrain(terrainId));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable UUID terrainId,
            @PathVariable UUID attachmentId
    ) {
        AttachmentService.AttachmentBinary bin = attachmentService.download(terrainId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bin.mimeType()))
                .contentLength(bin.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + bin.originalName() + "\"")
                .body(bin.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID terrainId,
            @PathVariable UUID attachmentId,
            @RequestParam UUID user_id
    ) {
        attachmentService.delete(terrainId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
