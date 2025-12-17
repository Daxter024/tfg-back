package com.agro.authservice.kafka;

import com.agro.authservice.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserDeleted(UserDeletedEvent event) {
        log.info("Publishing UserDeletedEvent: {}", event);
        kafkaTemplate.send("user-deleted", event.userId().toString(), event);
    }
}
