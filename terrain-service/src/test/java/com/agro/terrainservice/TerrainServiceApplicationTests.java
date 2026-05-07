package com.agro.terrainservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test del arranque del contexto. Esta deshabilitado mientras
 * <code>terrain-service</code> dependa de PostGIS (la entidad {@link
 * com.agro.terrainservice.entity.Terrain} mapea {@code Polygon} JTS, lo que
 * requiere {@code PostgisDialect} y un driver con la extension instalada). H2
 * en modo PostgreSQL no expone PostGIS, por lo que el contexto no carga.
 *
 * <p>Cuando el monorepo incorpore {@code Testcontainers} con la imagen
 * {@code postgis/postgis:15-3.5} en este servicio, retirar la anotacion
 * {@link Disabled} y dejar que el smoke test arranque el contexto real.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requiere PostGIS. Reactivar cuando se integre Testcontainers postgis.")
class TerrainServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
