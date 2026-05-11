package com.agro.inputservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Decide cuando emitir {@code stock-low}. Stub temporal — la logica completa
 * (anti-spam 24h, transicion above/below, payload con createdBy) llega en el
 * siguiente commit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    /**
     * Re-evalua el estado de stock-low del input y publica el evento si
     * procede. La logica completa llega en el commit que anade
     * {@code EventPublisher} y {@code stock_alert_log}.
     */
    public void checkAndEmitLowStock(UUID inputId) {
        // No-op por ahora (commit 4 lo rellena).
        log.debug("StockAlertService.checkAndEmitLowStock({}) — stub", inputId);
    }
}
