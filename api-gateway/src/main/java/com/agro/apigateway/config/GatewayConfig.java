package com.agro.apigateway.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
//    @Bean
//    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
//        // TODO: es más escalable si se usa yml pq se le puede meter los valores desde un docker-compose
//        return builder.routes()
//                .route("season-service", r -> r
//                        .path("/api/season/**")
//                        .filters(f -> f
//                                .stripPrefix(1))
//                        .uri("http://localhost:8084")
//                ).build();
//    }
}
