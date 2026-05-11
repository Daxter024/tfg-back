package com.agro.taskservice.controller;

import com.agro.taskservice.constants.TaskField;
import com.agro.taskservice.dto.PageResponse;
import com.agro.taskservice.dto.TaskCalendarSlotDTO;
import com.agro.taskservice.dto.TaskRequest;
import com.agro.taskservice.dto.TaskSummaryDTO;
import com.agro.taskservice.dto.TaskUpdateRequest;
import com.agro.taskservice.repository.TaskRepository;
import com.agro.taskservice.service.I18nService;
import com.agro.taskservice.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints REST principales del recurso Task — HU-TAR-01.
 * El user actual se inyecta por la cabecera {@code X-User-Id} (la pone el
 * api-gateway tras validar el JWT). Esto evita acoplar task-service al secreto
 * del JWT y mantiene la responsabilidad de autenticacion en el gateway.
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final I18nService i18nService;

    /* ============================ writes ============================ */

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody TaskRequest request,
            @RequestHeader(value = "X-User-Id") UUID userId) {
        UUID id = taskService.createTask(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "id", id,
                        "message", i18nService.getMessage("task.created")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        taskService.updateTask(id, request);
        return ResponseEntity.ok(Map.of("message", i18nService.getMessage("task.updated")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    /* ============================ reads ============================= */

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOne(
            @PathVariable UUID id,
            @RequestParam(required = false) List<TaskField> fields) {
        return ResponseEntity.ok(taskService.getTask(id, fields));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskSummaryDTO>> list(
            @RequestParam(required = false) UUID assigned_to,
            @RequestParam(required = false) UUID created_by,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String task_type_code,
            @RequestParam(required = false) UUID terrain_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TaskRepository.TaskFilters filters = new TaskRepository.TaskFilters(
                assigned_to, created_by,
                csv(state), csv(task_type_code), terrain_id,
                from, to, overdue, null);
        return ResponseEntity.ok(taskService.listTasks(filters, page, size));
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<TaskCalendarSlotDTO>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) UUID assigned_to,
            @RequestParam(required = false) UUID terrain_id,
            @RequestParam(defaultValue = "month") String view) {
        if (!List.of("week", "month", "year").contains(view)) {
            return ResponseEntity.badRequest().build();
        }
        TaskRepository.TaskFilters filters = new TaskRepository.TaskFilters(
                assigned_to, null, null, null, terrain_id,
                null, null, null, null);
        return ResponseEntity.ok(taskService.calendar(from, to, filters));
    }

    /* ----------------------------- utils ----------------------------- */

    private static List<String> csv(String s) {
        if (s == null || s.isBlank()) return null;
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
