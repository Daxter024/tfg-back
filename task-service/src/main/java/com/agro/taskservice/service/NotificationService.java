package com.agro.taskservice.service;

import com.agro.taskservice.dto.NotificationPreferenceDTO;
import com.agro.taskservice.model.Notification;
import com.agro.taskservice.model.NotificationPreference;
import com.agro.taskservice.repository.NotificationPreferenceRepository;
import com.agro.taskservice.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-TAR-04 — bandeja + preferencias + anti-spam (hub D5).
 *
 * <p>Las notifs upcoming/overdue las crea el {@link
 * NotificationSchedulerService}; este servicio expone la logica reutilizable
 * (createFromStockLow, createFromSensorAlert, anti-spam, dedup).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Limite por encima del cual las overdue del dia se agrupan en TASK_DIGEST. */
    public static final int OVERDUE_DIGEST_THRESHOLD = 10;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    /* ============================ inbox ============================ */

    @Transactional(readOnly = true)
    public java.util.List<Notification> inbox(UUID userId, int page, int size) {
        return notificationRepository.findInbox(userId, page, size);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.unreadCount(userId);
    }

    @Transactional
    public int markRead(UUID notificationId, UUID userId) {
        return notificationRepository.markRead(notificationId, userId);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    /* ========================== preferences ======================== */

    @Transactional(readOnly = true)
    public NotificationPreference getPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId).orElse(defaultPreference(userId));
    }

    @Transactional
    public void upsertPreferences(UUID userId, NotificationPreferenceDTO dto) {
        String json = dto.task_type_lead_minutes() == null ? null
                : serialize(dto.task_type_lead_minutes());
        preferenceRepository.upsert(userId,
                Boolean.TRUE.equals(dto.email_enabled()),
                Boolean.TRUE.equals(dto.in_app_enabled()),
                dto.default_lead_minutes() == null ? 1440 : dto.default_lead_minutes(),
                json,
                dto.quiet_hours_start(),
                dto.quiet_hours_end(),
                Boolean.TRUE.equals(dto.also_notify_creator()));
    }

    /* =================== upcoming/overdue creation =================== */

    @Transactional
    public void createUpcoming(UUID userId, UUID taskId, String taskTypeCode,
                                LocalDateTime plannedAt, String channel) {
        if (notificationRepository.existsByTaskKind(taskId, "TASK_UPCOMING", userId)) {
            return;
        }
        String subject = i18nService.getMessage("notification.task.upcoming.subject", taskTypeCode);
        String body = i18nService.getMessage("notification.task.upcoming.body", taskTypeCode, plannedAt.toString());
        notificationRepository.create(userId, taskId, "TASK_UPCOMING", null, channel, subject, body);
    }

    @Transactional
    public void createOverdue(UUID userId, UUID taskId, String taskTypeCode,
                               LocalDateTime plannedAt, String channel) {
        if (notificationRepository.existsByTaskKindToday(taskId, "TASK_OVERDUE", userId)) {
            return;
        }
        String subject = i18nService.getMessage("notification.task.overdue.subject", taskTypeCode);
        String body = i18nService.getMessage("notification.task.overdue.body", taskTypeCode, plannedAt.toString());
        notificationRepository.create(userId, taskId, "TASK_OVERDUE", null, channel, subject, body);

        if (notificationRepository.countOverdueToday(userId) > OVERDUE_DIGEST_THRESHOLD) {
            int n = notificationRepository.deleteOverdueToday(userId);
            String dSubject = i18nService.getMessage("notification.task.digest.subject", n);
            String dBody = i18nService.getMessage("notification.task.digest.body", n);
            notificationRepository.create(userId, null, "TASK_DIGEST", null, channel, dSubject, dBody);
        }
    }

    /* =========== hub D5: stock-low & sensor-alert helpers =========== */

    /** Crea una notif STOCK_LOW si no se ha emitido una identica en las
     *  ultimas 24 h. */
    @Transactional
    public void createFromStockLow(UUID userId, UUID inputId, String inputName,
                                    java.math.BigDecimal currentStock, java.math.BigDecimal threshold, String unit) {
        if (alreadyEmittedRecently("STOCK_LOW", inputId, userId, Duration.ofHours(24))) {
            return;
        }
        String subject = i18nService.getMessage("notification.stock.low.subject", inputName);
        String body = i18nService.getMessage("notification.stock.low.body",
                inputName, currentStock == null ? "" : currentStock.toPlainString(),
                unit == null ? "" : unit,
                threshold == null ? "" : threshold.toPlainString());
        notificationRepository.create(userId, null, "STOCK_LOW", inputId, "IN_APP", subject, body);
        notificationRepository.logEmission("STOCK_LOW", inputId, userId);
    }

    /** Crea N notifs SENSOR_ALERT (una por destinatario). Si en la ultima hora
     *  ya hay > 5 alertas del mismo sensor para el user, agrupa en una sola
     *  fila "N alertas en sensor X". */
    @Transactional
    public void createFromSensorAlert(UUID userId, UUID sensorId, String variable,
                                       java.math.BigDecimal value, java.math.BigDecimal threshold) {
        int recent = notificationRepository.countEmissionsSince("SENSOR_ALERT", sensorId, userId,
                LocalDateTime.now().minusHours(1));
        if (recent > 5) {
            String subject = i18nService.getMessage("notification.sensor.alert.group.subject",
                    recent + 1, variable);
            String body = i18nService.getMessage("notification.sensor.alert.group.body",
                    variable, recent + 1,
                    value == null ? "" : value.toPlainString(),
                    threshold == null ? "" : threshold.toPlainString());
            notificationRepository.create(userId, null, "SENSOR_ALERT", sensorId, "IN_APP", subject, body);
        } else {
            String subject = i18nService.getMessage("notification.sensor.alert.subject", variable);
            String body = i18nService.getMessage("notification.sensor.alert.body",
                    variable, value == null ? "" : value.toPlainString(),
                    "", threshold == null ? "" : threshold.toPlainString());
            notificationRepository.create(userId, null, "SENSOR_ALERT", sensorId, "IN_APP", subject, body);
        }
        notificationRepository.logEmission("SENSOR_ALERT", sensorId, userId);
    }

    /** Borra notifs/prefs del user (politica D2). */
    @Transactional
    public void deleteByUserId(UUID userId) {
        notificationRepository.deleteByUserId(userId);
        preferenceRepository.deleteByUserId(userId);
    }

    /* ============================ helpers ============================ */

    public boolean alreadyEmittedRecently(String sourceKind, UUID sourceRef,
                                           UUID userId, Duration window) {
        return notificationRepository.countEmissionsSince(sourceKind, sourceRef, userId,
                LocalDateTime.now().minus(window)) > 0;
    }

    /** Devuelve true si {@code now} cae dentro de la franja silenciosa. */
    public boolean isQuietHours(NotificationPreference p, LocalTime now) {
        LocalTime s = p.quiet_hours_start();
        LocalTime e = p.quiet_hours_end();
        if (s == null || e == null) return false;
        if (s.equals(e)) return false;
        if (s.isBefore(e)) {
            return !now.isBefore(s) && now.isBefore(e);
        }
        // Cruza medianoche (p.ej. 22:00..07:00)
        return !now.isBefore(s) || now.isBefore(e);
    }

    public int resolveLead(NotificationPreference p, String taskTypeCode) {
        if (p.task_type_lead_minutes_json() == null) return p.default_lead_minutes();
        Map<String, Integer> map = deserializeLeadMap(p.task_type_lead_minutes_json());
        Integer override = map.get(taskTypeCode);
        return override == null ? p.default_lead_minutes() : override;
    }

    public NotificationPreference defaultPreference(UUID userId) {
        return new NotificationPreference(userId, true, true, 1440,
                null, null, null, false);
    }

    public Optional<NotificationPreference> findOptional(UUID userId) {
        return preferenceRepository.findByUserId(userId);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize preference map", e);
            return null;
        }
    }

    private Map<String, Integer> deserializeLeadMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse task_type_lead_minutes JSON", e);
            return Collections.emptyMap();
        }
    }
}
