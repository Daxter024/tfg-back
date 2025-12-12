package com.agro.terrainservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        DockerImageName postgisImage = DockerImageName.parse("postgis/postgis:15-3.5");
        DockerImageName compatibleImage = postgisImage.asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer<>(compatibleImage);
    }

}
