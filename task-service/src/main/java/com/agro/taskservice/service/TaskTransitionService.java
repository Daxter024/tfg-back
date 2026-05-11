package com.agro.taskservice.service;

import com.agro.taskservice.constants.TaskState;
import com.agro.taskservice.dto.ConsumedInput;
import com.agro.taskservice.dto.TaskStateTransitionRequest;
import com.agro.taskservice.event.TaskCompletedEvent;
import com.agro.taskservice.exception.InvalidStateTransitionException;
import com.agro.taskservice.exception.TaskNotFoundException;
import com.agro.taskservice.kafka.EventPublisher;
import com.agro.taskservice.model.Task;
import com.agro.taskservice.model.TaskType;
import com.agro.taskservice.repository.TaskEvidenceRepository;
import com.agro.taskservice.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HU-TAR-02 — maquina de transiciones de estado.
 *
 * <pre>
 * PENDING     -&gt; IN_PROGRESS | CANCELLED
 * IN_PROGRESS -&gt; FINISHED   | CANCELLED
 * FINISHED    -&gt; (inmutable)
 * CANCELLED   -&gt; (inmutable)
 * </pre>
 *
 * <p>Transicion FINISHED exige:
 * <ul>
 *   <li>{@code real_duration_minutes} no nulo;</li>
 *   <li>{@code consumed_inputs} no vacio;</li>
 *   <li>al menos 1 evidencia subida si el tipo es {@code TREATMENT} o
 *       {@code FERTILIZATION}.</li>
 * </ul>
 * Tras un FINISHED valido, publica {@link TaskCompletedEvent} a Kafka.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskTransitionService {

    private static final Map<TaskState, Set<TaskState>> ALLOWED = Map.of(
            TaskState.PENDING, EnumSet.of(TaskState.IN_PROGRESS, TaskState.CANCELLED),
            TaskState.IN_PROGRESS, EnumSet.of(TaskState.FINISHED, TaskState.CANCELLED),
            TaskState.FINISHED, EnumSet.noneOf(TaskState.class),
            TaskState.CANCELLED, EnumSet.noneOf(TaskState.class));

    private static final Set<String> EVIDENCE_REQUIRED_TYPES = Set.of("TREATMENT", "FERTILIZATION");

    private final TaskRepository taskRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final EventPublisher eventPublisher;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void transition(UUID taskId, TaskStateTransitionRequest req, UUID actorId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() ->
                new TaskNotFoundException(i18nService.getMessage("task.not.found", taskId)));

        TaskState current = TaskState.valueOf(task.state());
        TaskState target = req.to_state();
        if (!ALLOWED.getOrDefault(current, EnumSet.noneOf(TaskState.class)).contains(target)) {
            throw new InvalidStateTransitionException(
                    i18nService.getMessage("task.transition.invalid"));
        }

        LocalDateTime effective = req.effective_at() == null ? LocalDateTime.now() : req.effective_at();

        // Validaciones especificas por destino
        switch (target) {
            case IN_PROGRESS -> taskRepository.applyTransition(taskId, target.name(),
                    effective, null, null, null);
            case FINISHED -> finalize(task, req, effective);
            case CANCELLED -> taskRepository.applyTransition(taskId, target.name(),
                    null, null, null, null);
            default -> throw new InvalidStateTransitionException(
                    i18nService.getMessage("task.transition.invalid"));
        }

        taskRepository.insertHistory(taskId, current.name(), target.name(),
                actorId, effective, req.note());

        if (target == TaskState.FINISHED) {
            TaskType type = taskRepository.findTypeById(task.task_type_id())
                    .orElseThrow(() -> new InvalidStateTransitionException(
                            i18nService.getMessage("task.type.unknown", task.task_type_id())));
            eventPublisher.publishTaskCompleted(new TaskCompletedEvent(
                    taskId, type.code(), task.terrain_id(), task.assigned_to(),
                    effective, req.consumed_inputs()));
        }
    }

    private void finalize(Task task, TaskStateTransitionRequest req, LocalDateTime effective) {
        if (req.real_duration_minutes() == null) {
            throw new InvalidStateTransitionException(
                    i18nService.getMessage("task.real.duration.required"));
        }
        List<ConsumedInput> consumed = req.consumed_inputs();
        if (consumed == null || consumed.isEmpty()) {
            throw new InvalidStateTransitionException(
                    i18nService.getMessage("task.consumed.input.required"));
        }
        TaskType type = taskRepository.findTypeById(task.task_type_id())
                .orElseThrow(() -> new InvalidStateTransitionException(
                        i18nService.getMessage("task.type.unknown", task.task_type_id())));
        if (EVIDENCE_REQUIRED_TYPES.contains(type.code())
                && evidenceRepository.countByTaskId(task.id()) == 0) {
            throw new InvalidStateTransitionException(
                    i18nService.getMessage("task.evidence.required"));
        }
        taskRepository.applyTransition(task.id(), TaskState.FINISHED.name(),
                null, effective, req.real_duration_minutes(),
                serialize(consumed));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize consumed_inputs", e);
            return null;
        }
    }
}
