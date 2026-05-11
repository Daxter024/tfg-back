package com.agro.iotservice.listener;

import com.agro.iotservice.event.UserDeletedEvent;
import com.agro.iotservice.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * RGPD policy: when a user is deleted, drop the sensors they created. Unlike
 * task-service which keeps FINISHED tasks for the explotation logbook,
 * sensor data has no standalone historical value once the owner is gone;
 * cascade through sensor_reading / sensor_alert / device_api_key removes
 * everything downstream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedListener {

    private final SensorService sensorService;

    @KafkaListener(topics = "user-deleted", groupId = "iot-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        int removed = sensorService.deleteByCreatedBy(event.userId());
        log.info("user-deleted {} -> removed {} sensors", event.userId(), removed);
    }
}
