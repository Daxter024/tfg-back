package com.agro.iotservice.service;

import com.agro.iotservice.constants.AlertKind;
import com.agro.iotservice.constants.AlertState;
import com.agro.iotservice.event.SensorAlertEvent;
import com.agro.iotservice.exception.AlertNotFoundException;
import com.agro.iotservice.kafka.EventPublisher;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.model.SensorAlert;
import com.agro.iotservice.model.Threshold;
import com.agro.iotservice.repository.SensorAlertRepository;
import com.agro.iotservice.repository.SensorRepository;
import com.agro.iotservice.repository.ThresholdRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Threshold evaluation + alert deduplication. Plays as the AlertEvaluator for
 * {@link SensorReadingService}: every persisted reading runs through
 * {@link #evaluate(UUID, BigDecimal, Instant)} inside the same transaction.
 *
 * <p>Dedup logic (plan §7.2):
 * <ol>
 *   <li>Resolve threshold: sensor-scoped wins over variable-scoped; absent
 *       -> no alert.</li>
 *   <li>Classify: below_min / above_max / in_range.</li>
 *   <li>If out of range and an open alert of the same kind exists ->
 *       bump reading_count (no Kafka). If none -> INSERT and publish Kafka
 *       ONCE.</li>
 *   <li>If in range -> auto-resolve any open alerts on this sensor.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService implements SensorReadingService.AlertEvaluator {

    private final SensorReadingService readingService;
    private final SensorRepository sensorRepo;
    private final ThresholdRepository thresholdRepo;
    private final SensorAlertRepository alertRepo;
    private final EventPublisher publisher;
    private final I18nService i18n;

    @PostConstruct
    void wireIntoReadingService() {
        readingService.setAlertEvaluator(this);
    }

    @Override
    @Transactional
    public void evaluate(UUID sensorId, BigDecimal value, Instant recordedAt) {
        Sensor sensor = sensorRepo.findById(sensorId).orElse(null);
        if (sensor == null) return;

        Optional<Threshold> resolved = thresholdRepo.resolveForSensor(sensorId, sensor.variable());
        if (resolved.isEmpty()) return;
        Threshold t = resolved.get();

        AlertKind kind = classify(value, t.min_value(), t.max_value());
        if (kind == null) {
            // Reading is in-range -> close any open alerts for the sensor.
            int closed = alertRepo.autoResolveOpenForSensor(sensorId);
            if (closed > 0) {
                log.info("Auto-resolved {} alerts for sensor {} after in-range reading",
                        closed, sensorId);
            }
            return;
        }

        Optional<SensorAlert> existing = alertRepo.findOpenByKind(sensorId, kind);
        if (existing.isPresent()) {
            alertRepo.incrementCount(existing.get().id(), recordedAt);
            return;
        }

        UUID alertId = alertRepo.insert(sensorId, t.id(), kind, value, recordedAt);
        BigDecimal threshold = kind == AlertKind.below_min ? t.min_value() : t.max_value();
        SensorAlertEvent event = new SensorAlertEvent(
                alertId,
                sensorId,
                sensor.terrain_id(),
                sensor.variable().name(),
                kind.name(),
                value,
                threshold,
                recordedAt.truncatedTo(ChronoUnit.MILLIS),
                t.notify_user_ids() == null ? List.of() : t.notify_user_ids()
        );
        publisher.publishSensorAlert(event);
    }

    private AlertKind classify(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) return AlertKind.below_min;
        if (max != null && value.compareTo(max) > 0) return AlertKind.above_max;
        return null;
    }

    // ---------- review / resolve ----------

    @Transactional(readOnly = true)
    public SensorAlert getById(UUID id) {
        return alertRepo.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(i18n.getMessage("alert.not.found")));
    }

    @Transactional(readOnly = true)
    public List<SensorAlert> search(AlertState state, UUID terrainId, Instant from, Instant to) {
        return alertRepo.search(state, terrainId, from, to);
    }

    @Transactional
    public void review(UUID id, UUID reviewer, String comment) {
        if (alertRepo.findById(id).isEmpty()) {
            throw new AlertNotFoundException(i18n.getMessage("alert.not.found"));
        }
        alertRepo.markReviewed(id, reviewer, comment);
    }

    @Transactional
    public void resolve(UUID id) {
        if (alertRepo.findById(id).isEmpty()) {
            throw new AlertNotFoundException(i18n.getMessage("alert.not.found"));
        }
        alertRepo.markResolved(id);
    }
}
