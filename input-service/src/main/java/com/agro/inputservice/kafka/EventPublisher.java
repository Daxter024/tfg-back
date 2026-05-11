package com.agro.inputservice.kafka;

import com.agro.inputservice.event.StockLowEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor del topic {@code stock-low}. La key es el inputId — asi las
 * alertas del mismo insumo caen siempre en la misma particion y mantienen
 * orden temporal estable para el consumidor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStockLow(StockLowEvent event) {
        log.info("Publishing StockLowEvent input={} stock={} threshold={} createdBy={}",
                event.inputId(), event.currentStock(), event.threshold(), event.createdBy());
        kafkaTemplate.send("stock-low", event.inputId().toString(), event);
    }
}
