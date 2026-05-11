package com.agro.taskservice;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test que valida que (a) el ApplicationContext arranca,
 * (b) Flyway aplica V1+ sobre un Postgres 14 real (Testcontainers).
 *
 * <p>Usa el perfil {@code test} para evitar tocar la BBDD local y para que el
 * KafkaAdmin no haga fail-fast cuando no hay broker. Si el entorno de tests no
 * tiene un Docker daemon accesible (CI minimal, sandbox) se omite el test en
 * lugar de hacer fallar el build.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TaskServiceApplicationTests {

    @BeforeAll
    static void skipIfNoDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no esta disponible en este entorno; se omite el smoke test de contexto.");
    }

    @Test
    void contextLoads() {
    }
}
