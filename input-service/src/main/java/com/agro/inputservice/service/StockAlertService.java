package com.agro.inputservice.service;

import com.agro.inputservice.event.StockLowEvent;
import com.agro.inputservice.kafka.EventPublisher;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.repository.InputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decide cuando emitir {@code stock-low} y lo publica. Regla (§6.4 del plan):
 *
 * <ul>
 *   <li>Si el insumo no tiene {@code low_stock_threshold} configurado → nunca alerta.</li>
 *   <li>Stock cruza HACIA ABAJO el umbral → emite + marca {@code is_currently_below=true}.</li>
 *   <li>Stock sigue debajo (siguiente movimiento OUT) → re-emite SOLO si han pasado &gt; 24h.</li>
 *   <li>Stock cruza HACIA ARRIBA (recupera) → marca {@code is_currently_below=false} (no emite).</li>
 *   <li>Luego cruza hacia abajo otra vez → emite inmediatamente (no espera 24h).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    private static final Duration ANTI_SPAM = Duration.ofHours(24);

    private final InputRepository inputRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void checkAndEmitLowStock(UUID inputId) {
        Input input = inputRepository.findById(inputId).orElse(null);
        if (input == null || input.low_stock_threshold() == null) {
            return;
        }
        BigDecimal threshold = input.low_stock_threshold();
        BigDecimal currentStock = inputRepository.computeCurrentStock(inputId);
        boolean nowBelow = currentStock.compareTo(threshold) < 0;

        Optional<Map<String, Object>> logEntry = inputRepository.findAlertLog(inputId);
        boolean wasBelow = logEntry
                .map(m -> Boolean.TRUE.equals(m.get("is_currently_below")))
                .orElse(false);
        Instant lastEmitted = logEntry
                .map(m -> ((Timestamp) m.get("last_emitted_at")).toInstant())
                .orElse(Instant.EPOCH);

        if (nowBelow) {
            boolean canEmit = !wasBelow
                    || Duration.between(lastEmitted, Instant.now()).compareTo(ANTI_SPAM) > 0;
            if (canEmit) {
                StockLowEvent event = new StockLowEvent(
                        input.id(),
                        input.name(),
                        currentStock,
                        threshold,
                        input.unit(),
                        input.created_by());
                try {
                    eventPublisher.publishStockLow(event);
                } catch (Exception e) {
                    // No bloqueamos el flujo (movimiento ya esta persistido).
                    // El log queda para correlacion; el broker estara up en prod.
                    log.warn("publishStockLow failed for input={} — {}", inputId, e.getMessage());
                }
                inputRepository.upsertAlertLog(inputId, threshold, currentStock, true);
            } else {
                log.debug("stock-low suppressed for input={} (anti-spam window 24h)", inputId);
            }
        } else if (wasBelow) {
            // Cruza hacia arriba — marca above para que un futuro cruce abajo
            // se pueda emitir sin esperar 24h.
            inputRepository.markAlertLogAsAbove(inputId, threshold, currentStock);
        }
    }
}
