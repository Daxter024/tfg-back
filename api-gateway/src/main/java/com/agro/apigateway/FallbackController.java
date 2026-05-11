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
        return createFallbackResponse("season", "season-service");
    }

    @RequestMapping("/crop")
    public Mono<ResponseEntity<Map<String, Object>>> cropFallback() {
        return createFallbackResponse("crop", "crop-service");
    }

    @RequestMapping("/terrain")
    public Mono<ResponseEntity<Map<String, Object>>> terrainFallback() {
        return createFallbackResponse("terrain", "terrain-service");
    }

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return createFallbackResponse("auth", "auth-service");
    }

    @RequestMapping("/task")
    public Mono<ResponseEntity<Map<String, Object>>> taskFallback() {
        return createFallbackResponse("task", "task-service");
    }

    @RequestMapping("/input")
    public Mono<ResponseEntity<Map<String, Object>>> inputFallback() {
        return createFallbackResponse("input", "input-service");
    }

    @RequestMapping("/iot")
    public Mono<ResponseEntity<Map<String, Object>>> iotFallback() {
        return createFallbackResponse("iot", "iot-service");
    }

    private Mono<ResponseEntity<Map<String, Object>>> createFallbackResponse(String serviceName, String headerServiceName) {
        Map<String, Object> response = Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "code", 503,
                "message", serviceName + " service is temporarily unavailable",
                "timestamp", Instant.now(),
                "path", "/api/" + serviceName,
                "suggestedAction", "Please try again in a few moments",
                "support", "support@agro.com",
                "documentation", "https://docs.agro.com/api/troubleshooting",
                "fallback", true
        );
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("X-Fallback", "true")
                .header("X-Service", headerServiceName)
                .header("Retry-After", "30")
                .body(response));
    }
}
