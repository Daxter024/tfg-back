package com.agro.iotservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic produced by iot-service: {@code sensor-alert}. The hub (task-service)
 * consumes it via type-mapping and creates notifications per notifyUserId.
 * Topics consumed (terrain-deleted, user-deleted) are created by their
 * respective producers.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic sensorAlertTopic() {
        return TopicBuilder.name("sensor-alert")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
