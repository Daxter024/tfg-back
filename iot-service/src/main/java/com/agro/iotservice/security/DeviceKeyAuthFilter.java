package com.agro.iotservice.security;

import com.agro.iotservice.repository.DeviceApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates device-side reading ingest requests against the BCrypt key
 * hashes in {@code device_api_key}. Applies ONLY to {@code /ingest/**}; the
 * admin paths (/sensor, /threshold, /alert) are gated by the gateway JWT
 * filter upstream and bypass this filter entirely via shouldNotFilter.
 *
 * <p>Returns 401 with no body for both missing-key and wrong-key paths so the
 * filter cannot be used to enumerate valid sensor ids.</p>
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class DeviceKeyAuthFilter extends OncePerRequestFilter {

    private static final Pattern INGEST_PATH = Pattern.compile(
            "^/ingest/sensor/([0-9a-fA-F-]{36})/reading/?$");

    private final DeviceApiKeyRepository repo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/ingest/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        UUID sensorId = parseSensorId(req.getRequestURI());
        String key = req.getHeader("X-Device-Key");
        if (sensorId == null || key == null || key.isBlank()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        boolean ok;
        try {
            ok = repo.verifyActiveKey(sensorId, key);
        } catch (Exception e) {
            log.warn("Device-key verification raised: {}", e.getMessage());
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!ok) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(req, res);
    }

    private UUID parseSensorId(String uri) {
        Matcher m = INGEST_PATH.matcher(uri);
        if (!m.matches()) {
            return null;
        }
        try {
            return UUID.fromString(m.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
