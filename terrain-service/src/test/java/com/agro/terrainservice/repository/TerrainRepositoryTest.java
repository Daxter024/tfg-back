package com.agro.terrainservice.repository;

import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.service.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("TER-12.01 - existsById true tras INSERT")
    void existsById_returnsTrue_whenRowPresent() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                id, "Test", UUID.randomUUID());

        boolean exists = terrainRepository.existsById(id);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("TER-12.02 - existsById false con id aleatorio")
    void existsById_returnsFalse_whenRowMissing() {
        boolean exists = terrainRepository.existsById(UUID.randomUUID());
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("TER-12.03 - findIdsByUserId filtra por user_id")
    void findIdsByUserId_returnsAllOwnedIds() {
        UUID userId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)", a, "A", userId);
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)", b, "B", userId);
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), "Otro", UUID.randomUUID());

        List<UUID> ids = terrainRepository.findIdsByUserId(userId);
        assertThat(ids).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("TER-12.04 - deleteById borra exactamente 1 fila")
    void deleteById_removesExactlyOneRow() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                id, "X", UUID.randomUUID());

        terrainRepository.deleteById(id);

        boolean exists = terrainRepository.existsById(id);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("TER-12.05 - deleteTerrain con owner equivocado lanza TerrainNotFound")
    void deleteTerrain_throwsTerrainNotFound_whenOwnerMismatch() {
        UUID id = UUID.randomUUID();
        UUID realOwner = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                id, "X", realOwner);

        UUID wrongOwner = UUID.randomUUID();
        assertThatThrownBy(() -> terrainRepository.deleteTerrain(id, wrongOwner))
                .isInstanceOf(TerrainNotFoundException.class);

        // La fila NO se ha borrado
        assertThat(terrainRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("TER-12.06 - getTerrain con id inexistente lanza TerrainNotFound")
    void getTerrain_throwsTerrainNotFound_whenIdMissing() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> terrainRepository.getTerrain(id, "id, name"))
                .isInstanceOf(TerrainNotFoundException.class);
    }

    @Test
    @DisplayName("findIdsByUserId con user_id sin terrenos devuelve lista vacia")
    void findIdsByUserId_returnsEmpty_whenNoneOwned() {
        List<UUID> ids = terrainRepository.findIdsByUserId(UUID.randomUUID());
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("getTerrains con user_id sin terrenos devuelve lista vacia")
    void getTerrains_returnsEmpty_whenNoneOwned() {
        var rows = terrainRepository.getTerrains(UUID.randomUUID(), "id, name");
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("deleteTerrain del propietario devuelve sin excepcion y borra fila")
    void deleteTerrain_succeeds_whenOwnerMatches() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO terrain (id, name, user_id) VALUES (?, ?, ?)",
                id, "X", owner);

        terrainRepository.deleteTerrain(id, owner);

        assertThat(terrainRepository.existsById(id)).isFalse();
    }
}
