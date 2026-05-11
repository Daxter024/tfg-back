package com.agro.taskservice.service;

import com.agro.taskservice.dto.NotificationPreferenceDTO;
import com.agro.taskservice.model.NotificationPreference;
import com.agro.taskservice.repository.NotificationPreferenceRepository;
import com.agro.taskservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock I18nService i18nService;

    NotificationService service;

    @BeforeEach
    void setup() {
        service = new NotificationService(notificationRepository, preferenceRepository,
                i18nService, new ObjectMapper());
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createFromStockLow_first_creates() {
        UUID user = UUID.randomUUID();
        UUID input = UUID.randomUUID();
        when(notificationRepository.countEmissionsSince(eq("STOCK_LOW"), any(UUID.class),
                any(UUID.class), any(LocalDateTime.class))).thenReturn(0);

        service.createFromStockLow(user, input, "Glyphosate",
                new BigDecimal("3"), new BigDecimal("10"), "L");

        verify(notificationRepository, times(1)).create(eq(user), org.mockito.ArgumentMatchers.isNull(),
                eq("STOCK_LOW"), eq(input), eq("IN_APP"), anyString(), anyString());
        verify(notificationRepository).logEmission(eq("STOCK_LOW"), eq(input), eq(user));
    }

    @Test
    void createFromStockLow_dedupWithin24h() {
        UUID user = UUID.randomUUID();
        UUID input = UUID.randomUUID();
        when(notificationRepository.countEmissionsSince(eq("STOCK_LOW"), any(UUID.class),
                any(UUID.class), any(LocalDateTime.class))).thenReturn(1);

        service.createFromStockLow(user, input, "Glyphosate",
                new BigDecimal("3"), new BigDecimal("10"), "L");

        verify(notificationRepository, never()).create(any(), any(), anyString(), any(),
                anyString(), anyString(), anyString());
    }

    @Test
    void createFromSensorAlert_groupsAbove5_perHour() {
        UUID user = UUID.randomUUID();
        UUID sensor = UUID.randomUUID();
        when(notificationRepository.countEmissionsSince(eq("SENSOR_ALERT"), any(UUID.class),
                any(UUID.class), any(LocalDateTime.class))).thenReturn(6);

        service.createFromSensorAlert(user, sensor, "humidity",
                new BigDecimal("12"), new BigDecimal("20"));

        verify(notificationRepository).create(eq(user), org.mockito.ArgumentMatchers.isNull(),
                eq("SENSOR_ALERT"), eq(sensor), eq("IN_APP"),
                org.mockito.ArgumentMatchers.contains("group.subject"), anyString());
    }

    @Test
    void createOverdue_above10_collapsesToDigest() {
        UUID user = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        when(notificationRepository.existsByTaskKindToday(eq(task), eq("TASK_OVERDUE"), eq(user)))
                .thenReturn(false);
        when(notificationRepository.countOverdueToday(user)).thenReturn(11);

        service.createOverdue(user, task, "IRRIGATION", LocalDateTime.now().minusDays(1), "IN_APP");

        verify(notificationRepository).create(eq(user), eq(task), eq("TASK_OVERDUE"),
                org.mockito.ArgumentMatchers.isNull(), eq("IN_APP"), anyString(), anyString());
        verify(notificationRepository).deleteOverdueToday(user);
        verify(notificationRepository).create(eq(user), org.mockito.ArgumentMatchers.isNull(),
                eq("TASK_DIGEST"), org.mockito.ArgumentMatchers.isNull(), eq("IN_APP"),
                anyString(), anyString());
    }

    @Test
    void createUpcoming_dedup() {
        UUID user = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        when(notificationRepository.existsByTaskKind(task, "TASK_UPCOMING", user)).thenReturn(true);

        service.createUpcoming(user, task, "IRRIGATION", LocalDateTime.now().plusHours(2), "IN_APP");
        verify(notificationRepository, never()).create(any(), any(), anyString(), any(),
                anyString(), anyString(), anyString());
    }

    @Test
    void quietHours_crossingMidnight() {
        NotificationPreference p = new NotificationPreference(UUID.randomUUID(), true, true, 60,
                null, LocalTime.of(22, 0), LocalTime.of(7, 0), false);
        assertThat(service.isQuietHours(p, LocalTime.of(23, 30))).isTrue();
        assertThat(service.isQuietHours(p, LocalTime.of(3, 0))).isTrue();
        assertThat(service.isQuietHours(p, LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void resolveLead_perTypeOverride() {
        NotificationPreference p = new NotificationPreference(UUID.randomUUID(), true, true, 1440,
                "{\"TREATMENT\":2880,\"IRRIGATION\":120}", null, null, false);
        assertThat(service.resolveLead(p, "TREATMENT")).isEqualTo(2880);
        assertThat(service.resolveLead(p, "IRRIGATION")).isEqualTo(120);
        assertThat(service.resolveLead(p, "OTHER")).isEqualTo(1440); // fallback
    }

    @Test
    void upsertPreferences_serializesTypeMap() {
        UUID user = UUID.randomUUID();
        NotificationPreferenceDTO dto = new NotificationPreferenceDTO(true, true, 1440,
                java.util.Map.of("IRRIGATION", 60), null, null, false);
        service.upsertPreferences(user, dto);
        verify(preferenceRepository).upsert(eq(user), eq(true), eq(true), eq(1440),
                org.mockito.ArgumentMatchers.argThat((String s) -> s != null && s.contains("IRRIGATION")),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                eq(false));
    }

    @Test
    void deleteByUserId_deletesNotifsAndPrefs() {
        UUID user = UUID.randomUUID();
        service.deleteByUserId(user);
        verify(notificationRepository).deleteByUserId(user);
        verify(preferenceRepository).deleteByUserId(user);
    }

    @Test
    void getPreferences_emptyReturnsDefault() {
        UUID user = UUID.randomUUID();
        when(preferenceRepository.findByUserId(user)).thenReturn(Optional.empty());
        NotificationPreference p = service.getPreferences(user);
        assertThat(p.user_id()).isEqualTo(user);
        assertThat(p.default_lead_minutes()).isEqualTo(1440);
        assertThat(p.email_enabled()).isTrue();
    }

    @Test
    void alreadyEmittedRecently_truthful() {
        UUID user = UUID.randomUUID();
        UUID ref = UUID.randomUUID();
        when(notificationRepository.countEmissionsSince(eq("STOCK_LOW"), eq(ref), eq(user),
                any(LocalDateTime.class))).thenReturn(2);
        assertThat(service.alreadyEmittedRecently("STOCK_LOW", ref, user, Duration.ofHours(24))).isTrue();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
