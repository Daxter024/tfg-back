package com.agro.inputservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic producido por input-service: {@code stock-low}. Los topics que consume
 * (user-deleted, task-completed) los crean sus respectivos productores
 * (auth-service y task-service).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic stockLowTopic() {
        return TopicBuilder.name("stock-low")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
