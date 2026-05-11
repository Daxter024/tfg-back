package com.agro.taskservice.kafka;

import com.agro.taskservice.event.TaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor del topic {@code task-completed} — patron espejo del
 * EventPublisher de terrain-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTaskCompleted(TaskCompletedEvent event) {
        log.info("Publishing TaskCompletedEvent: {}", event);
        kafkaTemplate.send("task-completed", event.taskId().toString(), event);
    }
}
