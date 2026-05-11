package com.agro.iotservice.kafka;

import com.agro.iotservice.event.SensorAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer for the {@code sensor-alert} topic. Key = alertId (string) so all
 * later events for the same alert (none in v1) would route to the same
 * partition; ordering matters for downstream consumers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSensorAlert(SensorAlertEvent event) {
        log.info("Publishing SensorAlertEvent alert={} sensor={} kind={} value={}",
                event.alertId(), event.sensorId(), event.kind(), event.value());
        kafkaTemplate.send("sensor-alert", event.alertId().toString(), event);
    }
}
