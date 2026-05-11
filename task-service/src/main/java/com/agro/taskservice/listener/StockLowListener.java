package com.agro.taskservice.listener;

import com.agro.taskservice.event.StockLowEvent;
import com.agro.taskservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Hub D5 — convierte cada {@code stock-low} en una notif IN_APP
 * (anti-spam 24h dentro de NotificationService).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockLowListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "stock-low", groupId = "task-service-group")
    public void onStockLow(StockLowEvent event) {
        log.info("stock-low received: input={}", event.inputId());
        notificationService.createFromStockLow(event.createdBy(), event.inputId(),
                event.inputName(), event.currentStock(), event.threshold(), event.unit());
    }
}
