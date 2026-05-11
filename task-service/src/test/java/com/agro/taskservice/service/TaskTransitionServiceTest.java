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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskTransitionServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskEvidenceRepository evidenceRepository;
    @Mock EventPublisher eventPublisher;
    @Mock I18nService i18nService;

    TaskTransitionService service;

    @BeforeEach
    void setup() {
        service = new TaskTransitionService(taskRepository, evidenceRepository,
                eventPublisher, i18nService, new ObjectMapper());
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Task task(UUID id, TaskState state, int typeId) {
        return new Task(id, typeId, UUID.randomUUID(), LocalDateTime.now().plusDays(1), 60,
                state.name(), null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, null, null, LocalDateTime.now(), null);
    }

    @Test
    void pendingToFinished_isIllegal() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.PENDING, 2)));
        var req = new TaskStateTransitionRequest(TaskState.FINISHED,
                null, 30, List.of(new ConsumedInput("x", null, BigDecimal.ONE, "L")), "n");

        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(eventPublisher, never()).publishTaskCompleted(any());
    }

    @Test
    void finished_isImmutable() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.FINISHED, 2)));
        var req = new TaskStateTransitionRequest(TaskState.CANCELLED, null, null, null, null);
        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void pendingToInProgress_ok_recordsHistory() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.PENDING, 2)));
        var req = new TaskStateTransitionRequest(TaskState.IN_PROGRESS, null, null, null, "starting");

        service.transition(id, req, actor);

        verify(taskRepository).applyTransition(eq(id), eq("IN_PROGRESS"), any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        verify(taskRepository).insertHistory(eq(id), eq("PENDING"), eq("IN_PROGRESS"),
                eq(actor), any(), eq("starting"));
        verify(eventPublisher, never()).publishTaskCompleted(any());
    }

    @Test
    void finished_withoutRealDuration_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.IN_PROGRESS, 2)));
        var req = new TaskStateTransitionRequest(TaskState.FINISHED, null, null,
                List.of(new ConsumedInput("a", null, BigDecimal.ONE, "L")), null);
        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void finished_withoutConsumedInputs_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.IN_PROGRESS, 2)));
        var req = new TaskStateTransitionRequest(TaskState.FINISHED, null, 30, List.of(), null);
        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void finishedTreatment_withoutEvidence_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, TaskState.IN_PROGRESS, 4)));
        when(taskRepository.findTypeById(4)).thenReturn(Optional.of(new TaskType(4, "TREATMENT", "")));
        when(evidenceRepository.countByTaskId(id)).thenReturn(0);

        var req = new TaskStateTransitionRequest(TaskState.FINISHED, null, 30,
                List.of(new ConsumedInput("a", null, BigDecimal.ONE, "L")), null);
        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void finishedTreatment_withEvidence_publishesEvent() {
        UUID id = UUID.randomUUID();
        Task t = task(id, TaskState.IN_PROGRESS, 4);
        when(taskRepository.findById(id)).thenReturn(Optional.of(t));
        when(taskRepository.findTypeById(4)).thenReturn(Optional.of(new TaskType(4, "TREATMENT", "")));
        when(evidenceRepository.countByTaskId(id)).thenReturn(2);

        var req = new TaskStateTransitionRequest(TaskState.FINISHED, null, 90,
                List.of(new ConsumedInput("herb", null, new BigDecimal("3.5"), "L")), "done");

        service.transition(id, req, UUID.randomUUID());

        ArgumentCaptor<TaskCompletedEvent> captor = ArgumentCaptor.forClass(TaskCompletedEvent.class);
        verify(eventPublisher, times(1)).publishTaskCompleted(captor.capture());
        TaskCompletedEvent ev = captor.getValue();
        assertThat(ev.taskId()).isEqualTo(id);
        assertThat(ev.taskTypeCode()).isEqualTo("TREATMENT");
        assertThat(ev.consumedInputs()).hasSize(1);
        assertThat(ev.terrainId()).isEqualTo(t.terrain_id());
    }

    @Test
    void taskNotFound_throws404() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());
        var req = new TaskStateTransitionRequest(TaskState.IN_PROGRESS, null, null, null, null);
        assertThatThrownBy(() -> service.transition(id, req, UUID.randomUUID()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
