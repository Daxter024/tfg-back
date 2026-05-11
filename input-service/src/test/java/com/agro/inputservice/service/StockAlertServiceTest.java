package com.agro.inputservice.service;

import com.agro.inputservice.event.StockLowEvent;
import com.agro.inputservice.kafka.EventPublisher;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import com.agro.inputservice.repository.InputRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAlertServiceTest {

    @Mock InputRepository inputRepository;
    @Mock EventPublisher eventPublisher;

    @InjectMocks StockAlertService service;

    private final UUID inputId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();

    private Input withThreshold(BigDecimal threshold) {
        return new Input(inputId, "Urea", InputCategory.fertilizante, "kg",
                threshold, null, null, createdBy, Instant.now(), null, null, BigDecimal.ZERO);
    }

    @Test
    void no_threshold_means_no_emission() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(null)));
        service.checkAndEmitLowStock(inputId);
        verify(eventPublisher, never()).publishStockLow(any());
    }

    @Test
    void crosses_below_first_time_emits() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("3"));
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.empty());

        service.checkAndEmitLowStock(inputId);

        ArgumentCaptor<StockLowEvent> captor = ArgumentCaptor.forClass(StockLowEvent.class);
        verify(eventPublisher).publishStockLow(captor.capture());
        StockLowEvent ev = captor.getValue();
        assertThat(ev.inputId()).isEqualTo(inputId);
        assertThat(ev.currentStock()).isEqualByComparingTo("3");
        assertThat(ev.threshold()).isEqualByComparingTo("5");
        assertThat(ev.createdBy()).isEqualTo(createdBy);
        verify(inputRepository).upsertAlertLog(eq(inputId), any(), any(), eq(true));
    }

    @Test
    void stays_below_within_24h_does_not_re_emit() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("2"));
        Map<String, Object> log = Map.of(
                "is_currently_below", true,
                "last_emitted_at", Timestamp.from(Instant.now().minusSeconds(60))
        );
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.of(log));

        service.checkAndEmitLowStock(inputId);

        verify(eventPublisher, never()).publishStockLow(any());
    }

    @Test
    void stays_below_after_24h_re_emits() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("1"));
        Map<String, Object> log = Map.of(
                "is_currently_below", true,
                "last_emitted_at", Timestamp.from(Instant.now().minusSeconds(25 * 3600))
        );
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.of(log));

        service.checkAndEmitLowStock(inputId);

        verify(eventPublisher).publishStockLow(any());
    }

    @Test
    void crosses_above_marks_log_as_above_and_does_not_emit() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("20"));
        Map<String, Object> log = Map.of(
                "is_currently_below", true,
                "last_emitted_at", Timestamp.from(Instant.now().minusSeconds(60))
        );
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.of(log));

        service.checkAndEmitLowStock(inputId);

        verify(eventPublisher, never()).publishStockLow(any());
        verify(inputRepository).markAlertLogAsAbove(eq(inputId), any(), any());
    }

    @Test
    void crosses_above_then_below_emits_immediately() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("2"));
        // is_currently_below=false → puede emitir sin esperar 24h
        Map<String, Object> log = Map.of(
                "is_currently_below", false,
                "last_emitted_at", Timestamp.from(Instant.now().minusSeconds(60))
        );
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.of(log));

        service.checkAndEmitLowStock(inputId);

        verify(eventPublisher).publishStockLow(any());
        verify(inputRepository).upsertAlertLog(eq(inputId), any(), any(), eq(true));
    }

    @Test
    void above_with_no_log_does_nothing() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("20"));
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.empty());

        service.checkAndEmitLowStock(inputId);

        verify(eventPublisher, never()).publishStockLow(any());
        verify(inputRepository, never()).markAlertLogAsAbove(any(), any(), any());
    }

    @Test
    void publisher_failure_does_not_throw() {
        when(inputRepository.findById(inputId)).thenReturn(Optional.of(withThreshold(new BigDecimal("5"))));
        when(inputRepository.computeCurrentStock(inputId)).thenReturn(new BigDecimal("2"));
        when(inputRepository.findAlertLog(inputId)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down"))
                .when(eventPublisher).publishStockLow(any());

        service.checkAndEmitLowStock(inputId);

        // Aun marca el log como below para que el siguiente intento sepa que ya emitio
        verify(inputRepository).upsertAlertLog(eq(inputId), any(), any(), eq(true));
    }
}
