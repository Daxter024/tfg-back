package com.agro.apigateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {
    @RequestMapping("/season")
    public Mono<ResponseEntity<Map<String, Object>>> seasonFallback() {
        Map<String, Object> response = Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "code", 503,
                "message", "Season service is temporarily unavailable",
                "timestamp", Instant.now(),
                "path", "/api/season",
                "suggestedAction", "Please try again in a few moments",
                "support", "support@agro.com",
                "documentation", "https://docs.agro.com/api/troubleshooting",
                "fallback", true
        );
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("X-Fallback", "true")
                .header("X-Service", "season-service")
                .header("Retry-After", "30")
                .body(response));
    }
}
