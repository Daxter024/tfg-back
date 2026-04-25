package com.agro.terrainservice.client;

import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.exception.CadastralApiTimeoutException;
import com.agro.terrainservice.exception.CadastralApiUnavailableException;
import com.agro.terrainservice.exception.CadastralReferenceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;

/**
 * Cliente HTTP hacia la sede electrónica del Catastro. La integración real
 * requiere parsing de su servicio SOAP/JSON específico — en este TFG el
 * cliente queda como stub: si la URL no está configurada o la API real no
 * responde, devuelve {@code Optional.empty()} y deja que el frontend caiga
 * al alta manual.
 */
@Component
@Slf4j
public class CadastroClient {

    private final RestClient restClient;
    private final String baseUrl;

    public CadastroClient(@Value("${cadastro.api.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
    }

    public Optional<CadastralImportResponse> fetch(String reference) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("Cadastro API base URL not configured; returning empty for ref {}", reference);
            return Optional.empty();
        }
        try {
            // El contrato real del Catastro es SOAP; aquí dejamos el esqueleto
            // de llamada y devolvemos empty hasta que se mapee correctamente.
            // El test cubre el comportamiento de timeouts/errores.
            CadastralImportResponse body = restClient.get()
                    .uri(baseUrl + "/Consulta_DNPRC?refcat={ref}", reference)
                    .retrieve()
                    .body(CadastralImportResponse.class);
            return Optional.ofNullable(body);
        } catch (HttpClientErrorException.NotFound nf) {
            throw new CadastralReferenceNotFoundException("Reference not found at provider: " + reference);
        } catch (HttpServerErrorException sse) {
            throw new CadastralApiUnavailableException("Cadastro API returned 5xx: " + sse.getStatusCode());
        } catch (ResourceAccessException rae) {
            if (rae.getCause() instanceof SocketTimeoutException) {
                throw new CadastralApiTimeoutException("Cadastro API timeout");
            }
            throw new CadastralApiUnavailableException("Cadastro API not reachable: " + rae.getMessage());
        }
    }
}
