package com.agro.iotservice.service;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.Reading;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Dev-only emulator: every 30 s, generates a synthetic reading per active
 * sensor matching the units of its variable. Wires through the SAME
 * {@link SensorReadingService#persistAndEvaluate} as the HTTP path so
 * thresholds, alerts and last_reading_at are exercised end-to-end.
 *
 * <p>Disabled outside the {@code dev} profile so prod never injects fake
 * data.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class SensorSimulator {

    private final SensorRepository sensorRepo;
    private final SensorReadingService readingService;
    private final Random rng = new Random();

    @Scheduled(fixedDelay = 30_000)
    public void emit() {
        List<Sensor> active = sensorRepo.search(null, null, SensorStatus.active);
        if (active.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Sensor s : active) {
            BigDecimal v = synthesize(s.variable());
            readingService.persistAndEvaluate(s.id(), List.of(new Reading(now, v)));
        }
        log.debug("SensorSimulator: emitted {} readings", active.size());
    }

    private BigDecimal synthesize(VariableKind kind) {
        double v = switch (kind) {
            case temperature -> 10 + rng.nextDouble() * 25;     // 10-35 C
            case humidity     -> 30 + rng.nextDouble() * 60;    // 30-90 %
            case ph           -> 5.5 + rng.nextDouble() * 2.0;  // 5.5-7.5
            case soil_moisture -> 10 + rng.nextDouble() * 80;
            case wind_speed   -> rng.nextDouble() * 25;
            case rainfall     -> rng.nextDouble() * 30;
            case luminosity   -> rng.nextDouble() * 100_000;
            case other        -> rng.nextDouble() * 100;
        };
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
