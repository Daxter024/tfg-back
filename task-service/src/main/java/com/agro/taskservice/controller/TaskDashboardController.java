package com.agro.taskservice.controller;

import com.agro.taskservice.repository.TaskRepository;
import com.agro.taskservice.service.RoleScopingService;
import com.agro.taskservice.service.TaskDashboardService;
import com.agro.taskservice.service.TaskExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-TAR-03 — dashboard + export. Aplica role scoping (administrador,
 * agricultor, tecnico) usando las cabeceras X-User-Id y X-User-Role del
 * gateway.
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskDashboardController {

    private final TaskDashboardService dashboardService;
    private final TaskExportService exportService;
    private final RoleScopingService roleScopingService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(value = "X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String task_type_code,
            @RequestParam(required = false) UUID terrain_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        var filters = new TaskRepository.TaskFilters(null, null, null,
                csv(task_type_code), terrain_id, from, to, null, null);
        var scoped = roleScopingService.scope(filters, userId, role);
        return ResponseEntity.ok(dashboardService.dashboard(scoped));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestHeader(value = "X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) UUID assigned_to,
            @RequestParam(required = false) UUID created_by,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String task_type_code,
            @RequestParam(required = false) UUID terrain_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean overdue) {

        var filters = new TaskRepository.TaskFilters(assigned_to, created_by, csv(state),
                csv(task_type_code), terrain_id, from, to, overdue, null);
        var scoped = roleScopingService.scope(filters, userId, role);
        StreamingResponseBody body = exportService.exportCsv(scoped);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks.csv\"")
                .body(body);
    }

    private static List<String> csv(String s) {
        if (s == null || s.isBlank()) return null;
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
