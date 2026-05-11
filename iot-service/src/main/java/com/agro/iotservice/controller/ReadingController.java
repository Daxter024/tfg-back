package com.agro.iotservice.controller;

import com.agro.iotservice.constants.SensorAggregation;
import com.agro.iotservice.exception.InvalidReadingException;
import com.agro.iotservice.model.AggregatedBucket;
import com.agro.iotservice.model.SensorReading;
import com.agro.iotservice.repository.SensorReadingRepository;
import com.agro.iotservice.service.I18nService;
import com.agro.iotservice.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only endpoint for sensor readings. The {@code agg} query parameter
 * controls aggregation; when absent, a sensible default is picked based on
 * the {@code (to - from)} interval per plan §6.4.
 */
@RestController
@RequestMapping("/sensor/{id}/reading")
@RequiredArgsConstructor
public class ReadingController {

    private static final Duration RAW_LIMIT = Duration.ofHours(1);
    private static final Duration HOURLY_LIMIT = Duration.ofHours(24);
    private static final Duration DAILY_LIMIT = Duration.ofDays(31);
    private static final Duration MAX_RANGE = Duration.ofDays(365);

    private final SensorService sensorService;
    private final SensorReadingRepository repo;
    private final I18nService i18n;

    @GetMapping
    public ResponseEntity<Map<String, Object>> readings(
            @PathVariable UUID id,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) SensorAggregation agg) {

        // Touch the sensor — throws 404 if missing. Keeps the surface tidy.
        sensorService.getById(id);

        if (!from.isBefore(to)) {
            throw new InvalidReadingException(i18n.getMessage("reading.range.too-wide"));
        }
        Duration range = Duration.between(from, to);
        if (range.compareTo(MAX_RANGE) > 0) {
            throw new InvalidReadingException(i18n.getMessage("reading.range.too-wide"));
        }

        SensorAggregation resolved = agg != null ? agg : defaultAgg(range);
        boolean downsampled = range.compareTo(DAILY_LIMIT) > 0;

        Object body;
        switch (resolved) {
            case raw -> {
                List<SensorReading> raw = repo.findRaw(id, from, to);
                body = raw;
            }
            case hourly -> {
                List<AggregatedBucket> hourly = repo.findHourly(id, from, to);
                body = hourly;
            }
            case daily -> {
                List<AggregatedBucket> daily = repo.findDaily(id, from, to);
                body = daily;
            }
            default -> throw new InvalidReadingException(i18n.getMessage("reading.range.too-wide"));
        }

        HttpHeaders headers = new HttpHeaders();
        if (downsampled) {
            headers.add("X-Downsampled", "true");
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(Map.of("agg", resolved.name(), "data", body));
    }

    private SensorAggregation defaultAgg(Duration range) {
        if (range.compareTo(RAW_LIMIT) <= 0) return SensorAggregation.raw;
        if (range.compareTo(HOURLY_LIMIT) <= 0) return SensorAggregation.hourly;
        return SensorAggregation.daily;
    }
}
