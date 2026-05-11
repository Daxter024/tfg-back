package com.agro.taskservice.listener;

import com.agro.taskservice.event.SensorAlertEvent;
import com.agro.taskservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hub D5 — convierte cada {@code sensor-alert} en N notifs IN_APP
 * (una por destinatario en {@code notifyUserIds}). El anti-spam por
 * agrupacion vive en NotificationService.createFromSensorAlert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SensorAlertListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "sensor-alert", groupId = "task-service-group")
    public void onSensorAlert(SensorAlertEvent event) {
        log.info("sensor-alert received: sensor={} kind={} value={}",
                event.sensorId(), event.kind(), event.value());
        if (event.notifyUserIds() == null) return;
        for (UUID userId : event.notifyUserIds()) {
            notificationService.createFromSensorAlert(userId, event.sensorId(),
                    event.variable(), event.value(), event.threshold());
        }
    }
}
