package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ReferenceKind;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.exception.CadastralImportException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la integracion HTTP del CadastralImportService contra un proveedor
 * mockeado con WireMock. Cubre TER-9.13–9.22.
 *
 * <p>Usamos un I18nService real que devuelve la propia clave para simplificar
 * los asserts (no levantamos ApplicationContext).</p>
 */
class CadastralImportServiceWireMockTest {

    private WireMockServer wireMock;
    private CadastralImportService service;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        I18nService i18n = new StubI18nService();
        service = new CadastralImportService(i18n);
        ReflectionTestUtils.setField(service, "cadastroBaseUrl", wireMock.baseUrl());
        ReflectionTestUtils.setField(service, "sigpacBaseUrl", wireMock.baseUrl());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("TER-9.13 - Provider 200 con payload completo se mapea a la respuesta")
    void fetch_mapsAllFields_whenProviderReturnsFullPayload() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "name": "Parcela Norte",
                                  "geometry": {"type":"Polygon","coordinates":[]},
                                  "area_m2": 1234.5,
                                  "soil_use": "Agricola",
                                  "soil_class": "TA",
                                  "municipality": "Almeria",
                                  "province": "Almeria"
                                }
                                """)));

        CadastralImportResponse resp = service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL));

        assertThat(resp.reference()).isEqualTo(reference);
        assertThat(resp.suggested_name()).isEqualTo("Parcela Norte");
        assertThat(resp.area_m2()).isEqualTo(1234.5);
        assertThat(resp.soil_use()).isEqualTo("Agricola");
        assertThat(resp.soil_class()).isEqualTo("TA");
        assertThat(resp.municipality()).isEqualTo("Almeria");
        assertThat(resp.province()).isEqualTo("Almeria");
    }

    @Test
    @DisplayName("TER-9.14 - Provider 200 con campos parciales: faltantes vienen null")
    void fetch_partialFields() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "name": "Parcela Norte",
                                  "area_m2": 1000.0,
                                  "municipality": "Almeria"
                                }
                                """)));

        CadastralImportResponse resp = service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL));

        assertThat(resp.suggested_name()).isEqualTo("Parcela Norte");
        assertThat(resp.area_m2()).isEqualTo(1000.0);
        assertThat(resp.soil_class()).isNull();
        assertThat(resp.province()).isNull();
    }

    @Test
    @DisplayName("TER-9.15 - Provider 404 dispara 404 cadastral.reference.not.found")
    void fetch_throws404_whenProviderNotFound() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(404)));

        CadastralImportException ex = (CadastralImportException) catchThrowable(() ->
                service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL)));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("cadastral.reference.not.found");
    }

    @Test
    @DisplayName("TER-9.16 - Provider 400 dispara 502 cadastral.api.unavailable")
    void fetch_throws502_whenProvider400() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(400)));

        CadastralImportException ex = (CadastralImportException) catchThrowable(() ->
                service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL)));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).contains("cadastral.api.unavailable");
    }

    @Test
    @DisplayName("TER-9.17 - Provider 500 dispara 502")
    void fetch_throws502_whenProvider500() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(500)));

        CadastralImportException ex = (CadastralImportException) catchThrowable(() ->
                service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL)));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("TER-9.18 - Provider 503 dispara 502")
    void fetch_throws502_whenProvider503() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(503)));

        CadastralImportException ex = (CadastralImportException) catchThrowable(() ->
                service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL)));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("TER-9.20 - Provider apagado (conexion rechazada) dispara 504 cadastral.api.timeout")
    void fetch_throws504_whenProviderUnreachable() {
        // Apuntamos a un puerto donde no hay nada escuchando
        ReflectionTestUtils.setField(service, "cadastroBaseUrl", "http://127.0.0.1:1");

        assertThatThrownBy(() -> service.fetch(
                new CadastralImportRequest("1234ABCDEFGHIJKL5678", ReferenceKind.CADASTRAL)))
                .isInstanceOf(CadastralImportException.class)
                .satisfies(t -> {
                    CadastralImportException c = (CadastralImportException) t;
                    assertThat(c.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(c.getMessage()).contains("cadastral.api.timeout");
                });
    }

    @Test
    @DisplayName("TER-9.21 - area_m2 como string numerica se parsea correctamente")
    void fetch_parsesAreaAsString() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"area_m2":"123.45"}
                                """)));

        CadastralImportResponse resp = service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL));
        assertThat(resp.area_m2()).isEqualTo(123.45);
    }

    @Test
    @DisplayName("TER-9.22 - area_m2 como string no numerica devuelve null")
    void fetch_returnsNullArea_whenStringNotNumeric() {
        String reference = "1234ABCDEFGHIJKL5678";
        wireMock.stubFor(get(urlPathEqualTo("/" + reference))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"area_m2":"NaN-x"}
                                """)));

        CadastralImportResponse resp = service.fetch(new CadastralImportRequest(reference, ReferenceKind.CADASTRAL));
        assertThat(resp.area_m2()).isNull();
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("Expected throwable");
    }

    /** Stub minimo de I18nService que devuelve la clave como mensaje. */
    static class StubI18nService extends I18nService {
        StubI18nService() {
            super(new org.springframework.context.support.ResourceBundleMessageSource());
        }

        @Override
        public String getMessage(String code) {
            return code;
        }

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }
    }
}
