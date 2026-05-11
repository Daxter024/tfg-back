package com.agro.inputservice.listener;

import com.agro.inputservice.event.UserDeletedEvent;
import com.agro.inputservice.repository.InputRepository;
import com.agro.inputservice.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Politica RGPD-compatible: al recibir un {@code user-deleted}, anonymize los
 * movimientos del user (performed_by = NULL + marca en notes) y soft-delete sus
 * inputs (deleted_at = NOW()). Conservamos los movimientos para trazabilidad
 * historica del cuaderno de explotacion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedListener {

    private final MovementRepository movementRepository;
    private final InputRepository inputRepository;

    @KafkaListener(topics = "user-deleted", groupId = "input-service-group")
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("user-deleted received: {}", event.userId());
        int anon = movementRepository.anonymizePerformedBy(event.userId());
        int soft = inputRepository.softDeleteByCreatedBy(event.userId());
        log.info("user-deleted {} -> movements anon={} inputs soft-deleted={}",
                event.userId(), anon, soft);
    }
}
