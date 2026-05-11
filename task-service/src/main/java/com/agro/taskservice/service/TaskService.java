package com.agro.taskservice.service;

import com.agro.taskservice.client.TerrainGrpcClient;
import com.agro.taskservice.client.UserGrpcClient;
import com.agro.taskservice.constants.TaskField;
import com.agro.taskservice.constants.TaskState;
import com.agro.taskservice.dto.PageResponse;
import com.agro.taskservice.dto.RecurrenceSpec;
import com.agro.taskservice.dto.TaskCalendarSlotDTO;
import com.agro.taskservice.dto.TaskRequest;
import com.agro.taskservice.dto.TaskSummaryDTO;
import com.agro.taskservice.dto.TaskUpdateRequest;
import com.agro.taskservice.exception.InvalidFieldException;
import com.agro.taskservice.exception.RecurrenceExceededException;
import com.agro.taskservice.exception.TaskDeleteConflictException;
import com.agro.taskservice.exception.TaskNotFoundException;
import com.agro.taskservice.exception.TerrainNotFoundException;
import com.agro.taskservice.exception.UserNotFoundException;
import com.agro.taskservice.model.Task;
import com.agro.taskservice.model.TaskType;
import com.agro.taskservice.repository.TaskRepository;
import com.agro.taskservice.utils.FieldsValidator;
import com.agro.taskservice.utils.RecurrenceExpander;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio principal de tareas — HU-TAR-01 (planificacion + listado +
 * calendario + delete policy) + helpers reusados por HU-TAR-02..04 y por los
 * listeners Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    public static final UUID DELETED_USER_PLACEHOLDER =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final TaskRepository taskRepository;
    private final FieldsValidator fieldsValidator;
    private final TerrainGrpcClient terrainGrpcClient;
    private final UserGrpcClient userGrpcClient;
    private final RecurrenceExpander recurrenceExpander;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    /* ============================ create ============================ */

    @Transactional
    public UUID createTask(TaskRequest request, UUID createdBy) {
        TaskType type = taskRepository.findTypeByCode(request.task_type_code())
                .orElseThrow(() -> new InvalidFieldException(
                        i18nService.getMessage("task.type.unknown", request.task_type_code())));

        if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("task.terrain.not.found", request.terrain_id()));
        }
        validateUserExists(createdBy);
        validateUserExists(request.assigned_to());

        String plannedInputsJson = serializeJson(request.planned_inputs());
        String recurrenceRule = (request.recurrence() == null)
                ? null
                : "FREQ=" + request.recurrence().frequency()
                        + ";INTERVAL=" + request.recurrence().interval()
                        + ";UNTIL=" + request.recurrence().until();

        Task template = new Task(null, type.id(), request.terrain_id(), request.planned_at(),
                request.estimated_duration_minutes(), TaskState.PENDING.name(),
                null, null, null,
                createdBy, request.assigned_to(),
                null, recurrenceRule,
                request.notes(), plannedInputsJson, null,
                null, null);
        UUID parentId = taskRepository.insert(template);

        if (request.recurrence() != null) {
            expandAndInsertChildren(parentId, type.id(), request, plannedInputsJson,
                    createdBy, recurrenceRule);
        }
        return parentId;
    }

    private void expandAndInsertChildren(UUID parentId, Integer typeId, TaskRequest request,
                                          String plannedInputsJson, UUID createdBy,
                                          String recurrenceRule) {
        List<LocalDateTime> instances = recurrenceExpander.expand(request.planned_at(), request.recurrence());
        if (instances.size() > RecurrenceExpander.MAX_INSTANCES) {
            throw new RecurrenceExceededException(
                    i18nService.getMessage("task.recurrence.too-many-instances",
                            RecurrenceExpander.MAX_INSTANCES));
        }
        for (LocalDateTime at : instances) {
            Task child = new Task(null, typeId, request.terrain_id(), at,
                    request.estimated_duration_minutes(), TaskState.PENDING.name(),
                    null, null, null,
                    createdBy, request.assigned_to(),
                    parentId, recurrenceRule,
                    request.notes(), plannedInputsJson, null,
                    null, null);
            taskRepository.insert(child);
        }
    }

    private void validateUserExists(UUID userId) {
        if (DELETED_USER_PLACEHOLDER.equals(userId)) {
            return; // placeholder valido — no preguntar al gRPC
        }
        if (!userGrpcClient.validateUser(userId)) {
            throw new UserNotFoundException(
                    i18nService.getMessage("task.user.not.found", userId));
        }
    }

    /* ============================ update ============================ */

    @Transactional
    public void updateTask(UUID id, TaskUpdateRequest req) {
        if (taskRepository.findById(id).isEmpty()) {
            throw new TaskNotFoundException(i18nService.getMessage("task.not.found", id));
        }
        if (req.assigned_to() != null) {
            validateUserExists(req.assigned_to());
        }
        taskRepository.update(id, req.planned_at(), req.estimated_duration_minutes(),
                req.assigned_to(), req.notes());
    }

    /* ============================ delete ============================ */

    @Transactional
    public void deleteTask(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        i18nService.getMessage("task.not.found", id)));
        if (!TaskState.PENDING.name().equals(task.state()) || taskRepository.hasStateHistory(id)) {
            throw new TaskDeleteConflictException(
                    i18nService.getMessage("task.cannot.delete.with.history"));
        }
        taskRepository.delete(id);
    }

    /** Borrado en cascada al recibir {@code terrain-deleted}. */
    @Transactional
    public int deleteByTerrainId(UUID terrainId) {
        return taskRepository.deleteByTerrainId(terrainId);
    }

    /**
     * Politica D2 — al recibir {@code user-deleted}:
     * <ul>
     *   <li>Borrar fisicamente tareas en {@code PENDING}, {@code IN_PROGRESS}
     *       o {@code CANCELLED} donde el user es creator o assignee.</li>
     *   <li>Anonymizar (sustituir por {@link #DELETED_USER_PLACEHOLDER}) las
     *       tareas {@code FINISHED} donde el user es assignee.</li>
     *   <li>Idem para creator.</li>
     * </ul>
     * No anonymizamos {@code task_state_history.changed_by} — es traza de
     * auditoria y el user ya no existe, pero la fila se conserva como prueba
     * de actividad pasada.
     */
    @Transactional
    public UserDeletedSummary handleUserDeleted(UUID userId) {
        int deleted = taskRepository.deleteByUserIdAndStateIn(userId,
                List.of("PENDING", "IN_PROGRESS", "CANCELLED"));
        int anonAssignee = taskRepository.anonymizeAssigneeForFinished(userId, DELETED_USER_PLACEHOLDER);
        int anonCreator = taskRepository.anonymizeCreatorForFinished(userId, DELETED_USER_PLACEHOLDER);
        return new UserDeletedSummary(deleted, anonAssignee, anonCreator);
    }

    public record UserDeletedSummary(int deleted, int anonAssignee, int anonCreator) {
    }

    /* ============================ getters =========================== */

    @Transactional(readOnly = true)
    public Map<String, Object> getTask(UUID id, List<TaskField> fields) {
        String selectClause = fieldsValidator.formatFieldList(fields);
        try {
            return taskRepository.findByIdProjected(id, selectClause);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new TaskNotFoundException(i18nService.getMessage("task.not.found", id));
        }
    }

    @Transactional(readOnly = true)
    public Optional<Task> findById(UUID id) {
        return taskRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryDTO> listTasks(TaskRepository.TaskFilters filters, int page, int size) {
        long total = taskRepository.countWithFilters(filters);
        List<TaskSummaryDTO> content = taskRepository.findWithFilters(filters, page, size);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public List<TaskCalendarSlotDTO> calendar(LocalDateTime from, LocalDateTime to,
                                              TaskRepository.TaskFilters scopedFilters) {
        long days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        if (days < 0 || days > 400) {
            throw new InvalidFieldException(i18nService.getMessage("task.calendar.range.invalid"));
        }
        List<TaskSummaryDTO> rows = taskRepository.findCalendar(from, to, scopedFilters);
        List<TaskCalendarSlotDTO> slots = new ArrayList<>(rows.size());
        for (TaskSummaryDTO row : rows) {
            slots.add(toSlot(row));
        }
        return slots;
    }

    private TaskCalendarSlotDTO toSlot(TaskSummaryDTO row) {
        String title = row.task_type_code();
        String color = switch (row.state()) {
            case "FINISHED" -> "green";
            case "IN_PROGRESS" -> "blue";
            case "CANCELLED" -> "grey";
            default -> row.overdue() ? "red" : "yellow";
        };
        return new TaskCalendarSlotDTO(row.id(), title, row.planned_at(),
                row.estimated_duration_minutes(), row.state(), color);
    }

    /* ============================ helpers ============================ */

    public List<TaskType> taskTypes() {
        return taskRepository.findAllTypes();
    }

    private String serializeJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize JSON payload", e);
            return null;
        }
    }
}
