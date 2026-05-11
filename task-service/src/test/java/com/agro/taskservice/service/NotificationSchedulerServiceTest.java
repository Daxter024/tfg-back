package com.agro.taskservice.service;

import com.agro.taskservice.model.NotificationPreference;
import com.agro.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSchedulerServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock NotificationService notificationService;
    @Mock MailService mailService;

    NotificationSchedulerService scheduler;

    @BeforeEach
    void setup() {
        scheduler = new NotificationSchedulerService(taskRepository, notificationService, mailService);
        // The fields default-init to false; we want enabled = true
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void upcoming_outsideLeadWindow_doesNotEmit() {
        UUID task = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "id", task,
                "task_type_code", "IRRIGATION",
                "terrain_id", UUID.randomUUID(),
                "planned_at", Timestamp.valueOf(LocalDateTime.now().plusDays(5)),
                "assigned_to", assignee,
                "created_by", assignee);
        when(taskRepository.findUpcomingCandidates(any(LocalDateTime.class))).thenReturn(List.of(row));
        when(taskRepository.findOverdueCandidates(any(LocalDateTime.class))).thenReturn(List.of());
        NotificationPreference p = new NotificationPreference(assignee, true, true, 1440,
                null, null, null, false);
        when(notificationService.getPreferences(assignee)).thenReturn(p);
        when(notificationService.resolveLead(any(), anyString())).thenReturn(1440);

        scheduler.run();

        verify(notificationService, never()).createUpcoming(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void upcoming_insideLeadWindow_emits() {
        UUID task = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        LocalDateTime planned = LocalDateTime.now().plusMinutes(60); // 1 hora
        Map<String, Object> row = Map.of(
                "id", task,
                "task_type_code", "IRRIGATION",
                "terrain_id", UUID.randomUUID(),
                "planned_at", Timestamp.valueOf(planned),
                "assigned_to", assignee,
                "created_by", assignee);
        when(taskRepository.findUpcomingCandidates(any(LocalDateTime.class))).thenReturn(List.of(row));
        when(taskRepository.findOverdueCandidates(any(LocalDateTime.class))).thenReturn(List.of());
        NotificationPreference p = new NotificationPreference(assignee, true, true, 120 /* 2h */,
                null, null, null, false);
        when(notificationService.getPreferences(assignee)).thenReturn(p);
        when(notificationService.resolveLead(any(), anyString())).thenReturn(120);
        when(notificationService.isQuietHours(any(), any())).thenReturn(false);
        when(mailService.isEnabled()).thenReturn(true);

        scheduler.run();

        verify(notificationService, times(1)).createUpcoming(eq(assignee), eq(task),
                eq("IRRIGATION"), any(), eq("IN_APP"));
    }

    @Test
    void upcoming_alsoNotifyCreator_emitsTwice() {
        UUID task = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        LocalDateTime planned = LocalDateTime.now().plusMinutes(60);
        Map<String, Object> row = Map.of(
                "id", task,
                "task_type_code", "IRRIGATION",
                "terrain_id", UUID.randomUUID(),
                "planned_at", Timestamp.valueOf(planned),
                "assigned_to", assignee,
                "created_by", creator);
        when(taskRepository.findUpcomingCandidates(any(LocalDateTime.class))).thenReturn(List.of(row));
        when(taskRepository.findOverdueCandidates(any(LocalDateTime.class))).thenReturn(List.of());
        NotificationPreference p = new NotificationPreference(assignee, true, true, 120,
                null, null, null, true);
        when(notificationService.getPreferences(assignee)).thenReturn(p);
        NotificationPreference pc = new NotificationPreference(creator, true, true, 120,
                null, null, null, false);
        when(notificationService.getPreferences(creator)).thenReturn(pc);
        when(notificationService.resolveLead(any(), anyString())).thenReturn(120);
        when(notificationService.isQuietHours(any(), any())).thenReturn(false);

        scheduler.run();

        verify(notificationService).createUpcoming(eq(assignee), eq(task), anyString(), any(), eq("IN_APP"));
        verify(notificationService).createUpcoming(eq(creator), eq(task), anyString(), any(), eq("IN_APP"));
    }

    @Test
    void quietHours_skipEmail_butKeepInApp() {
        UUID task = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        LocalDateTime planned = LocalDateTime.now().plusMinutes(30);
        Map<String, Object> row = Map.of(
                "id", task,
                "task_type_code", "IRRIGATION",
                "terrain_id", UUID.randomUUID(),
                "planned_at", Timestamp.valueOf(planned),
                "assigned_to", assignee,
                "created_by", assignee);
        when(taskRepository.findUpcomingCandidates(any(LocalDateTime.class))).thenReturn(List.of(row));
        when(taskRepository.findOverdueCandidates(any(LocalDateTime.class))).thenReturn(List.of());
        NotificationPreference p = new NotificationPreference(assignee, true, true, 120,
                null, null, null, false);
        when(notificationService.getPreferences(assignee)).thenReturn(p);
        when(notificationService.resolveLead(any(), anyString())).thenReturn(120);
        when(notificationService.isQuietHours(any(), any())).thenReturn(true);
        when(mailService.isEnabled()).thenReturn(true);

        scheduler.run();

        verify(notificationService).createUpcoming(eq(assignee), eq(task), anyString(), any(), eq("IN_APP"));
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void overdue_emitsForAssignee() {
        UUID task = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "id", task,
                "task_type_code", "TREATMENT",
                "terrain_id", UUID.randomUUID(),
                "planned_at", Timestamp.valueOf(LocalDateTime.now().minusDays(2)),
                "assigned_to", assignee,
                "created_by", assignee);
        when(taskRepository.findUpcomingCandidates(any(LocalDateTime.class))).thenReturn(List.of());
        when(taskRepository.findOverdueCandidates(any(LocalDateTime.class))).thenReturn(List.of(row));
        NotificationPreference p = new NotificationPreference(assignee, true, true, 1440,
                null, null, null, false);
        when(notificationService.getPreferences(assignee)).thenReturn(p);
        when(notificationService.isQuietHours(any(), any())).thenReturn(false);

        scheduler.run();

        verify(notificationService).createOverdue(eq(assignee), eq(task), eq("TREATMENT"), any(), eq("IN_APP"));
    }
}
