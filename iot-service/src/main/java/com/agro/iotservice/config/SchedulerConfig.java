package com.agro.iotservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled annotation processor explicitly for the iot
 * module. The application class also carries {@code @EnableScheduling} so this
 * config is mainly an anchor for future @Scheduled tuning.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
