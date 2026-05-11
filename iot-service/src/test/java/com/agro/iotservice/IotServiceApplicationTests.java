package com.agro.iotservice;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test that validates (a) the ApplicationContext boots and (b) Flyway
 * applies V1 onto a real Postgres 14 (Testcontainers, NOT timescale — D3).
 *
 * <p>Uses the test profile so the KafkaAdmin does not fail-fast in absence of
 * a broker. Skipped if Docker is unavailable.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class IotServiceApplicationTests {

    @BeforeAll
    static void skipIfNoDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available in this environment; skipping smoke test.");
    }

    @Test
    void contextLoads() {
    }
}
