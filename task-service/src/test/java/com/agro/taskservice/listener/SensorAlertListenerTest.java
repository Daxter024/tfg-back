package com.agro.taskservice.listener;

import com.agro.taskservice.event.SensorAlertEvent;
import com.agro.taskservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SensorAlertListenerTest {

    @Mock NotificationService notificationService;
    @InjectMocks SensorAlertListener listener;

    @Test
    void onSensorAlert_creates_one_per_recipient() {
        UUID sensor = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        SensorAlertEvent ev = new SensorAlertEvent(UUID.randomUUID(), sensor,
                UUID.randomUUID(), "humidity", "below_min",
                new BigDecimal("10"), new BigDecimal("20"),
                Instant.now(), List.of(a, b, c));

        listener.onSensorAlert(ev);

        verify(notificationService, times(1)).createFromSensorAlert(eq(a), eq(sensor),
                anyString(), any(), any());
        verify(notificationService, times(1)).createFromSensorAlert(eq(b), eq(sensor),
                anyString(), any(), any());
        verify(notificationService, times(1)).createFromSensorAlert(eq(c), eq(sensor),
                anyString(), any(), any());
    }

    @Test
    void onSensorAlert_emptyRecipients_doesNothing() {
        SensorAlertEvent ev = new SensorAlertEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "humidity", "below_min",
                new BigDecimal("10"), new BigDecimal("20"),
                Instant.now(), null);

        listener.onSensorAlert(ev);

        verify(notificationService, never()).createFromSensorAlert(
                any(UUID.class), any(UUID.class), anyString(), any(), any());
    }
}
