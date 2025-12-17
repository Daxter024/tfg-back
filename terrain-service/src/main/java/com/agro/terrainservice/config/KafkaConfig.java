package com.agro.terrainservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic terrainDeletedTopic() {
        return TopicBuilder.name("terrain-deleted")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
