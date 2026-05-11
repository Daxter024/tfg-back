package com.agro.taskservice.service;

import com.agro.taskservice.client.TerrainGrpcClient;
import com.agro.taskservice.client.UserGrpcClient;
import com.agro.taskservice.repository.TaskRepository;
import com.agro.taskservice.utils.FieldsValidator;
import com.agro.taskservice.utils.RecurrenceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre los 3 casos de la politica D2 (ver §10.1 del plan):
 * <ol>
 *   <li>delete masivo de PENDING/IN_PROGRESS/CANCELLED,</li>
 *   <li>anonymize assigned_to en FINISHED,</li>
 *   <li>anonymize created_by en FINISHED.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceUserDeletedTest {

    @Mock TaskRepository taskRepository;
    @Mock TerrainGrpcClient terrainGrpcClient;
    @Mock UserGrpcClient userGrpcClient;
    @Mock I18nService i18nService;
    FieldsValidator fieldsValidator = new FieldsValidator();
    RecurrenceExpander recurrenceExpander = new RecurrenceExpander();
    ObjectMapper objectMapper = new ObjectMapper();

    TaskService service;

    @BeforeEach
    void setup() {
        service = new TaskService(taskRepository, fieldsValidator, terrainGrpcClient,
                userGrpcClient, recurrenceExpander, i18nService, objectMapper);
    }

    @Test
    void delegatesThreeRepoCalls_andReturnsSummary() {
        UUID user = UUID.randomUUID();
        when(taskRepository.deleteByUserIdAndStateIn(eq(user),
                eq(List.of("PENDING", "IN_PROGRESS", "CANCELLED")))).thenReturn(4);
        when(taskRepository.anonymizeAssigneeForFinished(user, TaskService.DELETED_USER_PLACEHOLDER))
                .thenReturn(2);
        when(taskRepository.anonymizeCreatorForFinished(user, TaskService.DELETED_USER_PLACEHOLDER))
                .thenReturn(1);

        var summary = service.handleUserDeleted(user);

        assertThat(summary.deleted()).isEqualTo(4);
        assertThat(summary.anonAssignee()).isEqualTo(2);
        assertThat(summary.anonCreator()).isEqualTo(1);
        verify(taskRepository).deleteByUserIdAndStateIn(user,
                List.of("PENDING", "IN_PROGRESS", "CANCELLED"));
        verify(taskRepository).anonymizeAssigneeForFinished(user, TaskService.DELETED_USER_PLACEHOLDER);
        verify(taskRepository).anonymizeCreatorForFinished(user, TaskService.DELETED_USER_PLACEHOLDER);
    }
}
