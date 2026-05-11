package com.agro.iotservice.service;

import com.agro.iotservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flags sensors whose last_reading_at is older than 2x their
 * expected interval as {@code no_signal}. The fixedDelay is 60_000 ms — the
 * scheduler granularity does not need to be tighter for the TFG.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SensorStatusScheduler {

    private final SensorRepository repository;

    @Scheduled(fixedDelay = 60_000)
    public void markStaleSensorsNoSignal() {
        int n = repository.markNoSignalIfStale();
        if (n > 0) {
            log.info("SensorStatusScheduler: marked {} sensors as no_signal", n);
        }
    }
}
