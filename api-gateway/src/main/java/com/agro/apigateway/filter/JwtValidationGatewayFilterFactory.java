package com.agro.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Valida el JWT contra auth-service y propaga las claims relevantes al
 * downstream como cabeceras {@code X-User-Id} y {@code X-User-Role}.
 *
 * <p>auth-service ya verifica la firma y la revocación al servir {@code
 * /validate}; aquí solo decodificamos el payload (Base64URL) para extraer
 * las claims y mutar el request.</p>
 *
 * <p>Defensa anti-spoof: las cabeceras {@code X-User-Id} y {@code
 * X-User-Role} que vengan del cliente son <strong>descartadas</strong>; solo
 * las pone este filtro tras validar el JWT.</p>
 */
@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationGatewayFilterFactory.class);

    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_ROLE = "X-User-Role";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/register"
    );

    public JwtValidationGatewayFilterFactory(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url}") String authServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            if (PUBLIC_PATHS.contains(path)) {
                return chain.filter(exchange);
            }

            String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (token == null || !token.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return webClient.get()
                    .uri("/validate")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .toBodilessEntity()
                    .flatMap(resp -> {
                        Claims claims = parseClaims(token.substring(7));
                        if (claims == null) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        ServerHttpRequest mutated = request.mutate()
                                .headers(h -> {
                                    h.remove(HDR_USER_ID);
                                    h.remove(HDR_USER_ROLE);
                                    if (claims.userId != null) h.set(HDR_USER_ID, claims.userId);
                                    if (claims.role != null) h.set(HDR_USER_ROLE, claims.role);
                                })
                                .build();
                        return chain.filter(exchange.mutate().request(mutated).build());
                    });
        };
    }

    /**
     * Decodifica el payload de un JWT (segmento intermedio en Base64URL) y
     * extrae las claims {@code userId} y {@code role}. NO re-verifica la
     * firma: auth-service ya lo hizo en {@code /validate}.
     *
     * @return Claims con userId/role, o null si el formato es inválido.
     */
    private Claims parseClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
            String userId = node.hasNonNull("userId") ? node.get("userId").asText() : null;
            String role = node.hasNonNull("role") ? node.get("role").asText() : null;
            return new Claims(userId, role);
        } catch (Exception e) {
            log.warn("Failed to decode JWT payload: {}", e.getMessage());
            return null;
        }
    }

    private record Claims(String userId, String role) {}
}
