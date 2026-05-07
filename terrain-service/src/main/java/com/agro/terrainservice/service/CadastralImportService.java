package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ReferenceKind;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.exception.CadastralImportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * HU-TER-05: importacion de terrenos desde el Catastro o SIGPAC.
 *
 * <p>El servicio valida el formato sintactico de la referencia antes de llamar
 * a la API externa. Las APIs reales requieren credenciales y endpoints que no
 * estan disponibles en el entorno del TFG, asi que la implementacion actual
 * delega en clientes RestClient configurables ({@code cadastro.api.base-url},
 * {@code sigpac.api.base-url}) y, si no estan apuntados a un host real, falla
 * de forma controlada con {@link HttpStatus#BAD_GATEWAY} para que el cliente
 * caiga al alta manual.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CadastralImportService {

    private static final Pattern CADASTRAL_PATTERN = Pattern.compile("^[0-9A-Z]{20}$");
    private static final Pattern SIGPAC_PATTERN =
            Pattern.compile("^\\d{2}-\\d{3}-\\d{1,2}-\\d{1,2}-\\d{1,3}-\\d{1,3}-\\d+$");

    private final I18nService i18nService;

    @Value("${cadastro.api.base-url:}")
    private String cadastroBaseUrl;

    @Value("${sigpac.api.base-url:}")
    private String sigpacBaseUrl;

    public CadastralImportResponse fetch(CadastralImportRequest request) {
        validateReference(request.kind(), request.reference());
        return switch (request.kind()) {
            case CADASTRAL -> fetchFromProvider(cadastroBaseUrl, request.reference(), "cadastro");
            case SIGPAC -> fetchFromProvider(sigpacBaseUrl, request.reference(), "sigpac");
        };
    }

    private void validateReference(ReferenceKind kind, String reference) {
        boolean ok = switch (kind) {
            case CADASTRAL -> CADASTRAL_PATTERN.matcher(reference).matches();
            case SIGPAC -> SIGPAC_PATTERN.matcher(reference).matches();
        };
        if (!ok) {
            throw new CadastralImportException(
                    HttpStatus.BAD_REQUEST,
                    i18nService.getMessage("cadastral.reference.malformed")
            );
        }
    }

    private CadastralImportResponse fetchFromProvider(String baseUrl, String reference, String providerName) {
        if (baseUrl == null || baseUrl.isBlank()) {
            // No hay endpoint configurado: el servicio externo no esta integrado todavia.
            // Devolvemos 502 para alinearnos con el contrato y permitir el fallback manual.
            log.warn("No base URL configured for {} provider; failing gracefully", providerName);
            throw new CadastralImportException(
                    HttpStatus.BAD_GATEWAY,
                    i18nService.getMessage("cadastral.api.unavailable")
            );
        }

        try {
            RestClient client = RestClient.builder().baseUrl(baseUrl).build();
            Map<String, Object> raw = client.get()
                    .uri("/{ref}", reference)
                    .retrieve()
                    .body(Map.class);
            return mapToResponse(reference, raw == null ? Map.of() : raw);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CadastralImportException(
                    HttpStatus.NOT_FOUND,
                    i18nService.getMessage("cadastral.reference.not.found"),
                    ex
            );
        } catch (HttpClientErrorException ex) {
            throw new CadastralImportException(
                    HttpStatus.BAD_GATEWAY,
                    i18nService.getMessage("cadastral.api.unavailable"),
                    ex
            );
        } catch (HttpServerErrorException ex) {
            throw new CadastralImportException(
                    HttpStatus.BAD_GATEWAY,
                    i18nService.getMessage("cadastral.api.unavailable"),
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new CadastralImportException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    i18nService.getMessage("cadastral.api.timeout"),
                    ex
            );
        } catch (CadastralImportException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error contacting {}", providerName, ex);
            throw new CadastralImportException(
                    HttpStatus.BAD_GATEWAY,
                    i18nService.getMessage("cadastral.api.unavailable"),
                    ex
            );
        }
    }

    @SuppressWarnings("unchecked")
    private CadastralImportResponse mapToResponse(String reference, Map<String, Object> raw) {
        return new CadastralImportResponse(
                reference,
                stringField(raw, "name"),
                (Map<String, Object>) raw.getOrDefault("geometry", Map.of()),
                doubleField(raw, "area_m2"),
                stringField(raw, "soil_use"),
                stringField(raw, "soil_class"),
                stringField(raw, "municipality"),
                stringField(raw, "province")
        );
    }

    private static String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static Double doubleField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
