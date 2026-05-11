package com.agro.iotservice.service;

import com.agro.iotservice.dto.Reading;
import com.agro.iotservice.repository.SensorReadingRepository;
import com.agro.iotservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core domain service for sensor readings. Idempotent ingestion with
 * (sensor_id, recorded_at) primary key, plus an optional bridge to
 * {@code AlertEvaluator} (registered as a bean in commit 5) so threshold
 * evaluation runs inside the same transaction as the insert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorReadingService {

    private final SensorReadingRepository readingRepo;
    private final SensorRepository sensorRepo;

    /** Optional bridge. Set by commit 5 (AlertEvaluator). Null = no-op. */
    @Autowired(required = false)
    private AlertEvaluator alertEvaluator;

    /**
     * Persists each reading idempotently, bumps {@code sensor.last_reading_at}
     * to max(recorded_at) and (if wired) evaluates thresholds per reading.
     *
     * @return number of rows actually inserted (excluding duplicates).
     */
    @Transactional
    public int persistAndEvaluate(UUID sensorId, List<Reading> readings) {
        if (readings == null || readings.isEmpty()) {
            return 0;
        }

        // Insert in chronological order — alert dedup expects increasing time.
        List<Reading> sorted = readings.stream()
                .sorted(Comparator.comparing(Reading::recorded_at))
                .toList();

        int inserted = 0;
        Instant max = null;
        for (Reading r : sorted) {
            int rows = readingRepo.insertIfAbsent(sensorId, r.recorded_at(), r.value());
            inserted += rows;
            if (rows > 0) {
                if (max == null || r.recorded_at().isAfter(max)) {
                    max = r.recorded_at();
                }
                if (alertEvaluator != null) {
                    alertEvaluator.evaluate(sensorId, r.value(), r.recorded_at());
                }
            }
        }
        if (max != null) {
            sensorRepo.touchOnReading(sensorId, max);
        }
        return inserted;
    }

    /**
     * SPI hook implemented in commit 5 ({@link com.agro.iotservice.service.AlertService}).
     * Kept as a tiny functional interface to avoid a hard dependency between
     * this service and the (later-introduced) threshold evaluation logic.
     */
    public interface AlertEvaluator {
        void evaluate(UUID sensorId, java.math.BigDecimal value, Instant recordedAt);
    }

    /** Bean accessor used by tests to inject a custom evaluator. */
    public void setAlertEvaluator(AlertEvaluator evaluator) {
        this.alertEvaluator = evaluator;
    }

    public Optional<AlertEvaluator> getAlertEvaluator() {
        return Optional.ofNullable(alertEvaluator);
    }
}
