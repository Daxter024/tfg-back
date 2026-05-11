package com.agro.taskservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-TAR-03 — role scoping del agricultor.
 *
 * <p>task-service no conoce la relacion {@code terrain.user_id} (vive en
 * terrain-db). Para listar las tareas de un agricultor necesita primero pedir
 * a terrain-service "dame los terrenos cuyo dueno es este user_id". La
 * llamada se hace por REST (no gRPC) porque el resultado es una lista — gRPC
 * en main solo expone {@code CheckTerrainExists}.</p>
 */
@Component
@Slf4j
public class TerrainHttpClient {

    private final RestClient restClient;

    public TerrainHttpClient(@Value("${terrain-service.base-url:http://localhost:8080}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Devuelve los {@code terrain_id} del agricultor. Si la llamada falla,
     * devuelve una lista vacia para que el caller no incluya ningun terreno
     * (mejor "no ver nada" que "ver todo" en un dashboard).
     */
    public List<UUID> findTerrainIdsByUser(UUID userId) {
        try {
            List<Map<String, Object>> raw = restClient.method(HttpMethod.GET)
                    .uri(uri -> uri.path("/terrain")
                            .queryParam("user_id", userId)
                            .queryParam("fields", "id")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (raw == null) return Collections.emptyList();
            return raw.stream()
                    .map(m -> m.get("id"))
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .map(UUID::fromString)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch terrains for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
