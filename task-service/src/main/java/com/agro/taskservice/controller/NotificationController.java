package com.agro.taskservice.controller;

import com.agro.taskservice.dto.NotificationPreferenceDTO;
import com.agro.taskservice.model.Notification;
import com.agro.taskservice.model.NotificationPreference;
import com.agro.taskservice.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-TAR-04 — bandeja + preferencias del user actual. El userId se inyecta
 * por la cabecera X-User-Id (igual que en TaskController).
 */
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> inbox(
            @RequestHeader(value = "X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.inbox(userId, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unread(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Integer>> read(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        int n = notificationService.markRead(id, userId);
        return ResponseEntity.ok(Map.of("updated", n));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllRead(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(userId)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreference> getPreferences(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Void> upsertPreferences(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody NotificationPreferenceDTO dto) {
        notificationService.upsertPreferences(userId, dto);
        return ResponseEntity.noContent().build();
    }
}
