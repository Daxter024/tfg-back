package com.agro.taskservice.service;

import com.agro.taskservice.model.NotificationPreference;
import com.agro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-TAR-04 — scheduler @Scheduled cada 60s:
 * <ul>
 *   <li>Crea notifs {@code TASK_UPCOMING} cuando {@code planned_at - lead}
 *       cruza el "ahora".</li>
 *   <li>Crea notifs {@code TASK_OVERDUE} (1 por dia, agrupando en TASK_DIGEST
 *       si > 10).</li>
 *   <li>Respeta {@code quiet_hours_start..quiet_hours_end} para EMAIL (no para
 *       IN_APP).</li>
 *   <li>Si {@code also_notify_creator=true}, genera una notif extra para
 *       {@code created_by}.</li>
 * </ul>
 *
 * <p>El scheduler se puede desactivar via {@code task.scheduler.enabled=false}
 * (perfil test).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulerService {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Value("${task.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${task.scheduler.delay-ms:60000}")
    public void tick() {
        if (!enabled) return;
        try {
            run();
        } catch (Exception e) {
            log.warn("Notification scheduler tick failed", e);
        }
    }

    /** Procesa upcoming + overdue una vez. Visible para tests. */
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        processUpcoming(now);
        processOverdue(now);
    }

    private void processUpcoming(LocalDateTime now) {
        List<Map<String, Object>> rows = taskRepository.findUpcomingCandidates(now);
        for (Map<String, Object> row : rows) {
            UUID assignee = (UUID) row.get("assigned_to");
            UUID creator = (UUID) row.get("created_by");
            UUID taskId = (UUID) row.get("id");
            String typeCode = (String) row.get("task_type_code");
            LocalDateTime plannedAt = ((Timestamp) row.get("planned_at")).toLocalDateTime();

            NotificationPreference assigneePref = notificationService.getPreferences(assignee);
            int lead = notificationService.resolveLead(assigneePref, typeCode);
            LocalDateTime threshold = plannedAt.minusMinutes(lead);
            if (now.isBefore(threshold)) {
                continue; // Aun no entro en ventana
            }
            emit(assignee, taskId, typeCode, plannedAt, true);

            if (assigneePref.also_notify_creator() && !assignee.equals(creator)) {
                emit(creator, taskId, typeCode, plannedAt, true);
            }
        }
    }

    private void processOverdue(LocalDateTime now) {
        List<Map<String, Object>> rows = taskRepository.findOverdueCandidates(now);
        for (Map<String, Object> row : rows) {
            UUID assignee = (UUID) row.get("assigned_to");
            UUID taskId = (UUID) row.get("id");
            String typeCode = (String) row.get("task_type_code");
            LocalDateTime plannedAt = ((Timestamp) row.get("planned_at")).toLocalDateTime();
            emit(assignee, taskId, typeCode, plannedAt, false);
        }
    }

    private void emit(UUID userId, UUID taskId, String typeCode, LocalDateTime plannedAt,
                       boolean upcoming) {
        NotificationPreference p = notificationService.getPreferences(userId);
        // IN_APP siempre se crea (anti-spam dentro del service); EMAIL respeta quiet hours
        if (p.in_app_enabled()) {
            if (upcoming) {
                notificationService.createUpcoming(userId, taskId, typeCode, plannedAt, "IN_APP");
            } else {
                notificationService.createOverdue(userId, taskId, typeCode, plannedAt, "IN_APP");
            }
        }
        if (p.email_enabled() && !notificationService.isQuietHours(p, LocalTime.now())
                && mailService.isEnabled()) {
            String subject = "[Task] " + typeCode + " — " + (upcoming ? "upcoming" : "overdue");
            mailService.send(userId.toString(), subject, "Task " + taskId + " planned at " + plannedAt);
        }
    }

    public Duration windowFor(NotificationPreference p, String typeCode) {
        return Duration.ofMinutes(notificationService.resolveLead(p, typeCode));
    }
}
