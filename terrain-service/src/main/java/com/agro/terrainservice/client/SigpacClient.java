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
import java.util.Optional;

/**
 * Cliente HTTP hacia SIGPAC. Mismo contrato que {@link CadastroClient}:
 * stub en este iteración, devuelve empty si no está configurado.
 */
@Component
@Slf4j
public class SigpacClient {

    private final RestClient restClient;
    private final String baseUrl;

    public SigpacClient(@Value("${sigpac.api.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
    }

    public Optional<CadastralImportResponse> fetch(String reference) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("SIGPAC API base URL not configured; returning empty for ref {}", reference);
            return Optional.empty();
        }
        try {
            CadastralImportResponse body = restClient.get()
                    .uri(baseUrl + "/parcel?ref={ref}", reference)
                    .retrieve()
                    .body(CadastralImportResponse.class);
            return Optional.ofNullable(body);
        } catch (HttpClientErrorException.NotFound nf) {
            throw new CadastralReferenceNotFoundException("Reference not found at provider: " + reference);
        } catch (HttpServerErrorException sse) {
            throw new CadastralApiUnavailableException("SIGPAC API returned 5xx: " + sse.getStatusCode());
        } catch (ResourceAccessException rae) {
            if (rae.getCause() instanceof SocketTimeoutException) {
                throw new CadastralApiTimeoutException("SIGPAC API timeout");
            }
            throw new CadastralApiUnavailableException("SIGPAC API not reachable: " + rae.getMessage());
        }
    }
}
