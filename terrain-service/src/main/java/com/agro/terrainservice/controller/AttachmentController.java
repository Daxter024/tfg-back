package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.AttachmentDTO;
import com.agro.terrainservice.service.AttachmentService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * HU-TER-03: endpoints de adjuntos por terreno. La autenticacion del usuario
 * se hace en el {@code api-gateway}; aqui solo aceptamos {@code user_id}
 * explicito (provisional, alineado con como funcionan ya {@code GET /terrain}
 * y {@code DELETE /terrain/{id}}).
 */
@RestController
@RequestMapping("/terrain/{terrainId}/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<AttachmentDTO> upload(
            @PathVariable UUID terrainId,
            @RequestParam UUID user_id,
            @RequestPart("file") MultipartFile file
    ) {
        AttachmentDTO dto = attachmentService.upload(terrainId, user_id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<AttachmentDTO>> list(
            @PathVariable UUID terrainId,
            @RequestParam UUID user_id
    ) {
        return ResponseEntity.ok(attachmentService.list(terrainId, user_id));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID terrainId,
            @PathVariable UUID attachmentId
    ) {
        AttachmentService.AttachmentResource res = attachmentService.download(terrainId, attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + res.attachment().original_name() + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(res.attachment().size_bytes())
                .contentType(MediaType.parseMediaType(res.attachment().mime_type()))
                .body(new InputStreamResource(res.stream()));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID terrainId,
            @PathVariable UUID attachmentId,
            @RequestParam UUID user_id
    ) {
        attachmentService.delete(terrainId, attachmentId, user_id);
        return ResponseEntity.noContent().build();
    }
}
