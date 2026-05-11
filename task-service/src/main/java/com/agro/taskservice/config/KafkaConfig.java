package com.agro.taskservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic producido por task-service: {@code task-completed}. Los topics que
 * consume (user-deleted, terrain-deleted, stock-low, sensor-alert) los crean
 * sus respectivos productores.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic taskCompletedTopic() {
        return TopicBuilder.name("task-completed")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
