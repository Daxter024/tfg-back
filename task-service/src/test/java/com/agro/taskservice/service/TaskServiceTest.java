package com.agro.taskservice.service;

import com.agro.taskservice.client.TerrainGrpcClient;
import com.agro.taskservice.client.UserGrpcClient;
import com.agro.taskservice.constants.TaskState;
import com.agro.taskservice.dto.RecurrenceSpec;
import com.agro.taskservice.dto.TaskRequest;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TerrainGrpcClient terrainGrpcClient;
    @Mock UserGrpcClient userGrpcClient;
    @Mock I18nService i18nService;
    FieldsValidator fieldsValidator = new FieldsValidator();
    RecurrenceExpander recurrenceExpander = new RecurrenceExpander();
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    TaskService service;

    @BeforeEach
    void setup() {
        service = new TaskService(taskRepository, fieldsValidator, terrainGrpcClient,
                userGrpcClient, recurrenceExpander, i18nService, objectMapper);
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private TaskRequest baseRequest() {
        return new TaskRequest("IRRIGATION",
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(2),
                60,
                UUID.randomUUID(),
                null, "note", null);
    }

    @Test
    void createTask_happyPath_returnsId() {
        TaskRequest req = baseRequest();
        UUID creator = UUID.randomUUID();
        UUID generated = UUID.randomUUID();

        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "task.type.irrigation")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, true));
        when(userGrpcClient.validateUser(any(UUID.class))).thenReturn(true);
        when(taskRepository.insert(any(Task.class))).thenReturn(generated);

        UUID id = service.createTask(req, creator);

        assertThat(id).isEqualTo(generated);
        verify(taskRepository).insert(any(Task.class));
    }

    @Test
    void createTask_unknownType_throws400() {
        TaskRequest req = baseRequest();
        when(taskRepository.findTypeByCode("IRRIGATION")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(InvalidFieldException.class);
        verify(taskRepository, never()).insert(any());
    }

    @Test
    void createTask_terrainMissing_throws404() {
        TaskRequest req = baseRequest();
        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(false, false));

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(TerrainNotFoundException.class);
    }

    @Test
    void createTask_assigneeMissing_throws404() {
        TaskRequest req = baseRequest();
        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, true));
        // creator validates, assignee doesn't
        when(userGrpcClient.validateUser(any(UUID.class))).thenReturn(true).thenReturn(false);

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void createTask_recurrenceWeeklyx10_inserts11rows() {
        // 1 parent + 10 children
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        TaskRequest req = new TaskRequest("IRRIGATION",
                UUID.randomUUID(), start, 60, UUID.randomUUID(),
                null, null,
                new RecurrenceSpec(RecurrenceSpec.Frequency.WEEKLY, 1, start.toLocalDate().plusWeeks(10)));

        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(any(UUID.class), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, true));
        when(userGrpcClient.validateUser(any(UUID.class))).thenReturn(true);
        when(taskRepository.insert(any(Task.class))).thenReturn(UUID.randomUUID());

        service.createTask(req, UUID.randomUUID());
        verify(taskRepository, times(11)).insert(any(Task.class));
    }

    @Test
    void createTask_recurrenceTooMany_throws() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        TaskRequest req = new TaskRequest("IRRIGATION",
                UUID.randomUUID(), start, 60, UUID.randomUUID(),
                null, null,
                new RecurrenceSpec(RecurrenceSpec.Frequency.DAILY, 1, LocalDate.now().plusYears(2)));

        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(any(UUID.class), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, true));
        when(userGrpcClient.validateUser(any(UUID.class))).thenReturn(true);
        when(taskRepository.insert(any(Task.class))).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(RecurrenceExceededException.class);
    }

    @Test
    void deleteTask_pendingWithoutHistory_ok() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(buildTask(id, TaskState.PENDING)));
        when(taskRepository.hasStateHistory(id)).thenReturn(false);

        service.deleteTask(id);
        verify(taskRepository).delete(id);
    }

    @Test
    void deleteTask_withHistory_conflict() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(buildTask(id, TaskState.PENDING)));
        when(taskRepository.hasStateHistory(id)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteTask(id))
                .isInstanceOf(TaskDeleteConflictException.class);
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void deleteTask_inProgress_conflict() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(buildTask(id, TaskState.IN_PROGRESS)));

        assertThatThrownBy(() -> service.deleteTask(id))
                .isInstanceOf(TaskDeleteConflictException.class);
    }

    @Test
    void deleteTask_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTask(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void calendar_rangeAboveOneYearPlusBuffer_throws() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = from.plusDays(500);
        assertThatThrownBy(() -> service.calendar(from, to, null))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void calendar_validRange_returnsList() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = from.plusDays(30);
        when(taskRepository.findCalendar(any(), any(), any())).thenReturn(List.of());
        assertThat(service.calendar(from, to, null)).isEmpty();
    }

    // -------------------- §27 ownership (D27 — TASK-27.NN) --------------------

    @Test
    void createTask_terrainNotOwned_throwsForbidden() {
        // TASK-27.02: terrain existe pero NO pertenece al user → 403
        TaskRequest req = baseRequest();
        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, false));

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(com.agro.taskservice.exception.ForbiddenException.class);
        verify(taskRepository, never()).insert(any());
    }

    @Test
    void createTask_terrainOwned_inserts() {
        // TASK-27.03: terrain existe Y pertenece al user → 201
        TaskRequest req = baseRequest();
        UUID generated = UUID.randomUUID();
        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(true, true));
        when(userGrpcClient.validateUser(any(UUID.class))).thenReturn(true);
        when(taskRepository.insert(any(Task.class))).thenReturn(generated);

        assertThat(service.createTask(req, UUID.randomUUID())).isEqualTo(generated);
        verify(taskRepository).insert(any(Task.class));
    }

    @Test
    void createTask_terrainGhost_throwsNotFoundBeforeForbidden() {
        // TASK-27.04: terrain inexistente → 404, no se llega a comprobar propiedad
        TaskRequest req = baseRequest();
        when(taskRepository.findTypeByCode("IRRIGATION"))
                .thenReturn(Optional.of(new TaskType(2, "IRRIGATION", "")));
        when(terrainGrpcClient.checkTerrainOwnership(eq(req.terrain_id()), any(UUID.class)))
                .thenReturn(new TerrainGrpcClient.Ownership(false, false));

        assertThatThrownBy(() -> service.createTask(req, UUID.randomUUID()))
                .isInstanceOf(TerrainNotFoundException.class);
        verify(taskRepository, never()).insert(any());
    }

    private Task buildTask(UUID id, TaskState state) {
        return new Task(id, 2, UUID.randomUUID(), LocalDateTime.now().plusDays(1), 60,
                state.name(), null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, null, null, LocalDateTime.now(), null);
    }
}
