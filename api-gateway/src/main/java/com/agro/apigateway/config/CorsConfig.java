package com.agro.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS global del api-gateway.
 *
 * <p>Aplica a TODAS las rutas (incluyendo respuestas 401/403 del filtro
 * JwtValidation), porque {@link CorsWebFilter} se ejecuta antes que el resto
 * de filtros del routing. Esto resuelve el problema típico de que un cliente
 * SPA reciba "No 'Access-Control-Allow-Origin' header" cuando el JWT es
 * inválido o falta — el preflight OPTIONS y la respuesta de error siguen
 * llevando las cabeceras CORS.</p>
 *
 * <p>Variables configurables vía env / application.yml:</p>
 * <ul>
 *   <li>{@code gateway.cors.allowed-origins} — CSV de orígenes permitidos.
 *       Default: {@code http://localhost:3000,http://localhost:5173}
 *       (Next.js / CRA y Vite respectivamente).</li>
 *   <li>{@code gateway.cors.max-age} — TTL de la cache de preflight en
 *       segundos. Default: {@code 3600}.</li>
 * </ul>
 *
 * <p>{@code allowCredentials=true} permite que el frontend mande
 * {@code Authorization: Bearer ...} con el JWT. Por requisito del estándar
 * CORS, con credentials NO se puede usar wildcard {@code *} en
 * {@code Allowed-Origin}; usamos {@code allowedOriginPatterns} para soportar
 * comodines locales sin perder seguridad.</p>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${gateway.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOrigins,
            @Value("${gateway.cors.max-age:3600}") long maxAge
    ) {
        CorsConfiguration config = new CorsConfiguration();

        // Origenes — uno por entrada del CSV. Usamos OriginPatterns para que
        // funcione bien con allowCredentials=true.
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOriginPattern(trimmed);
            }
        }

        // Métodos: todos los HTTP estándar + OPTIONS para preflight.
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));

        // Headers de entrada: cualquiera (Authorization, Content-Type,
        // X-User-Id si llegase de un cliente confiable, X-Device-Key para
        // /api/ingest/**, etc.).
        config.setAllowedHeaders(List.of(CorsConfiguration.ALL));

        // Headers que el frontend puede LEER de la respuesta. Útil para:
        //   - Authorization (refresh tokens en cabecera, si se diera el caso)
        //   - Content-Disposition (descarga de adjuntos/CSV con filename)
        //   - X-Downsampled (header marcador en iot-service §6.4)
        //   - Location (estándar HTTP en respuestas 201)
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "X-Downsampled",
                "Location"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
