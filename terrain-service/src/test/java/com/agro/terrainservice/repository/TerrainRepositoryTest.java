package com.agro.terrainservice.repository;

import com.agro.terrainservice.service.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice JDBC del {@link TerrainRepository} contra H2.
 *
 * <p>Limitaciones conocidas: H2 no expone PostGIS, asi que solo testeamos
 * operaciones que no invocan funciones espaciales (existsById, deleteById,
 * findIdsByUserId). El insert real con {@code ST_GeomFromGeoJSON} se cubre
 * por los tests unitarios del service y, cuando se integre Testcontainers
 * postgis, por un test de integracion.</p>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(TerrainRepository.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class TerrainRepositoryTest {

    @Autowired
    private TerrainRepository terrainRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private I18nService i18nService;

    @BeforeEach
    void setUp() {
        // Esquema simplificado: H2 sin PostGIS. Suficiente para existsById /
        // findIdsByUserId / deleteById.
        jdbcTemplate.execute("DROP TABLE IF EXISTS terrain");
        jdbcTemplate.execute("CREATE TABLE terrain (" +
                "id UUID DEFAULT random_uuid() PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "user_id UUID, " +
                "geometry VARCHAR(255)" +
                ")");
    }

    @Test
    void existsById_returnsTrue_whenRowPresent() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                id, "Test", UUID.randomUUID());

        boolean exists = terrainRepository.existsById(id);
        assertThat(exists).isTrue();
    }

    @Test
    void existsById_returnsFalse_whenRowMissing() {
        boolean exists = terrainRepository.existsById(UUID.randomUUID());
        assertThat(exists).isFalse();
    }

    @Test
    void findIdsByUserId_returnsAllOwnedIds() {
        UUID userId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)", a, "A", userId);
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)", b, "B", userId);
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), "Otro", UUID.randomUUID());

        var ids = terrainRepository.findIdsByUserId(userId);
        assertThat(ids).containsExactlyInAnyOrder(a, b);
    }
}
