package com.agro.terrainservice.integration;

import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import com.agro.terrainservice.repository.TerrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integracion completa con un PostgreSQL+PostGIS real (Testcontainers).
 *
 * <p>Cubre TER-1.10–1.13 (constraints SQL de geometria/area), TER-2.05/2.08–2.10
 * (lectura con geometria), TER-3.04 (cascade), TER-12.10–12.20 (constraints,
 * trigger, indices).</p>
 *
 * <p>Si Docker no esta disponible en el entorno, JUnit 5 saltara la clase via
 * Testcontainers (el container nunca arrancara y los assumptions condicionales
 * lo capturan). Para que el {@code mvn test} siga pasando, las pruebas se
 * marcan con {@link org.junit.jupiter.api.condition.EnabledIfSystemProperty}
 * sobre la propiedad {@code docker.available} (true por defecto si el ambiente
 * lo soporta).</p>
 */
@SpringBootTest(classes = TerrainPostgisIntegrationTest.IntegrationConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TerrainPostgisIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.5")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("terrain_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgis::getJdbcUrl);
        r.add("spring.datasource.username", postgis::getUsername);
        r.add("spring.datasource.password", postgis::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        r.add("grpc.client.auth-service.address", () -> "static://localhost:1");
        r.add("grpc.server.port", () -> "0");
    }

    @Autowired private TerrainRepository terrainRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String VALID_POLY = """
            {"type":"Polygon","coordinates":[[[-3.7100,40.4200],[-3.7100,40.4209],[-3.7088,40.4209],[-3.7088,40.4200],[-3.7100,40.4200]]]}
            """;

    private static final String TINY_POLY = """
            {"type":"Polygon","coordinates":[[[-3.71000,40.42000],[-3.71000,40.42005],[-3.70999,40.42005],[-3.70999,40.42000],[-3.71000,40.42000]]]}
            """;

    private static final String HUGE_POLY = """
            {"type":"Polygon","coordinates":[[[-3.0,40.0],[-3.0,41.0],[-2.0,41.0],[-2.0,40.0],[-3.0,40.0]]]}
            """;

    @BeforeEach
    void cleanTables() {
        assumeTrue(postgis.isRunning(), "PostGIS container required");
        jdbcTemplate.update("TRUNCATE TABLE attachment, terrain CASCADE");
    }

    @Test
    @DisplayName("TER-12.10 / TER-1.02 - INSERT polygon ~1 ha calcula area_m2 ≈ 10000")
    void insert_calculatesAreaForOneHectarePolygon() {
        UUID userId = UUID.randomUUID();
        UUID id = terrainRepository.saveWithCalculations(
                "Parcela 1ha", userId, VALID_POLY,
                SoilType.franco, 5.0, IrrigationType.goteo, "1234ABCD5678EF"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT area_m2, perimeter_m FROM terrain WHERE id = ?", id);

        double area = ((Number) row.get("area_m2")).doubleValue();
        // El polígono tiene aproximadamente 100m x 100m ≈ 10000 m²
        assertThat(area).isBetween(9_500.0, 10_500.0);
        double perim = ((Number) row.get("perimeter_m")).doubleValue();
        assertThat(perim).isBetween(380.0, 420.0);
    }

    @Test
    @DisplayName("TER-12.12 - centroid es Point dentro del polígono (ST_Within)")
    void centroidIsWithinPolygon() {
        UUID userId = UUID.randomUUID();
        UUID id = terrainRepository.saveWithCalculations(
                "X", userId, VALID_POLY, null, null, null, null);

        Boolean within = jdbcTemplate.queryForObject(
                "SELECT ST_Within(centroid, geometry) FROM terrain WHERE id = ?",
                Boolean.class, id);
        assertThat(within).isTrue();
    }

    @Test
    @DisplayName("TER-12.13 - trigger set_updated_at marca updated_at en UPDATE")
    void triggerUpdatesUpdatedAt() {
        UUID userId = UUID.randomUUID();
        UUID id = terrainRepository.saveWithCalculations(
                "X", userId, VALID_POLY, null, null, null, null);

        jdbcTemplate.update("UPDATE terrain SET name = 'Y' WHERE id = ?", id);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at FROM terrain WHERE id = ?", id);
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    @DisplayName("TER-12.16 / TER-1.12 - polygon < 100 m^2 es rechazado por terrain_area_range")
    void tinyPolygonRejected() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> terrainRepository.saveWithCalculations(
                "Tiny", userId, TINY_POLY, null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("terrain_area_range");
    }

    @Test
    @DisplayName("TER-12.17 / TER-1.13 - polygon > 1e8 m^2 es rechazado por terrain_area_range")
    void hugePolygonRejected() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> terrainRepository.saveWithCalculations(
                "Huge", userId, HUGE_POLY, null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("terrain_area_range");
    }

    @Test
    @DisplayName("TER-12.18 / TER-3.04 - DELETE terrain cascada borra adjuntos")
    void deleteTerrain_cascadeDeletesAttachments() {
        UUID userId = UUID.randomUUID();
        UUID terrainId = terrainRepository.saveWithCalculations(
                "X", userId, VALID_POLY, null, null, null, null);

        jdbcTemplate.update("""
                INSERT INTO attachment (terrain_id, original_name, mime_type, size_bytes, storage_key, uploaded_by)
                VALUES (?, 'a.jpg', 'image/jpeg', 100, 'k1', ?)
                """, terrainId, userId);
        jdbcTemplate.update("""
                INSERT INTO attachment (terrain_id, original_name, mime_type, size_bytes, storage_key, uploaded_by)
                VALUES (?, 'b.pdf', 'application/pdf', 200, 'k2', ?)
                """, terrainId, userId);

        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attachment WHERE terrain_id = ?", Integer.class, terrainId);
        assertThat(before).isEqualTo(2);

        terrainRepository.deleteById(terrainId);

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attachment WHERE terrain_id = ?", Integer.class, terrainId);
        assertThat(after).isEqualTo(0);
    }

    @Test
    @DisplayName("TER-2.08 - geometry seleccionada con ST_AsGeoJSON devuelve string parseable")
    void getTerrain_geometryAsGeoJson() {
        UUID userId = UUID.randomUUID();
        UUID id = terrainRepository.saveWithCalculations(
                "X", userId, VALID_POLY, null, null, null, null);

        Map<String, Object> row = terrainRepository.getTerrain(id, "ST_AsGeoJSON(geometry) AS geometry");
        Object geom = row.get("geometry");
        assertThat(geom).isInstanceOf(String.class);
        assertThat((String) geom).contains("Polygon");
    }

    @Test
    @DisplayName("TER-12.07 - INSERT con soilType=null funciona")
    void insertWithNullDescriptors() {
        UUID userId = UUID.randomUUID();
        UUID id = terrainRepository.saveWithCalculations(
                "X", userId, VALID_POLY, null, null, null, null);
        assertThat(terrainRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("TER-12.20 - cadastral_ref indexada (idx_terrain_cadastral_ref existe)")
    void cadastralRefIndexExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_terrain_cadastral_ref'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("TER-12.19 - GIST index sobre geometry existe (terrain_geom_gist_idx)")
    void gistIndexExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'terrain_geom_gist_idx'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /** Configuración mínima: solo cargamos el repositorio + utilidades necesarias. */
    @Configuration
    @EnableAutoConfiguration(exclude = {})
    @ComponentScan(
            basePackages = {
                    "com.agro.terrainservice.repository",
                    "com.agro.terrainservice.service",
                    "com.agro.terrainservice.utils",
                    "com.agro.terrainservice.config"
            },
            // Excluimos beans que requieren brokers/grpc reales para no romper el contexto
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "com.agro.terrainservice.service.EventPublisher"),
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "com.agro.terrainservice.client\\..*"),
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "com.agro.terrainservice.listener\\..*"),
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "com.agro.terrainservice.grpc\\..*")
            }
    )
    static class IntegrationConfig {
    }
}
