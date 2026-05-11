package com.agro.inputservice.service;

import com.agro.inputservice.constants.MovementReason;
import com.agro.inputservice.dto.MovementRequest;
import com.agro.inputservice.dto.PageResponse;
import com.agro.inputservice.exception.InputNotFoundException;
import com.agro.inputservice.exception.InvalidMovementException;
import com.agro.inputservice.exception.MovementNotFoundException;
import com.agro.inputservice.model.InputMovement;
import com.agro.inputservice.model.MovementKind;
import com.agro.inputservice.repository.InputRepository;
import com.agro.inputservice.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Casos de uso de {@link InputMovement}. La emision de eventos {@code
 * stock-low} la gestiona {@link StockAlertService} y se invoca al final de
 * cada movimiento (manual o por listener de task-completed).
 */
@Service
@RequiredArgsConstructor
public class MovementService {

    private final MovementRepository movementRepository;
    private final InputRepository inputRepository;
    private final StockAlertService stockAlertService;
    private final I18nService i18n;

    /**
     * Endpoint REST {@code POST /input/{id}/movement}. Bloquea {@code TASK} y
     * {@code TASK_REVERT} (reservados a flujo interno).
     */
    @Transactional
    public UUID recordManualMovement(UUID inputId, MovementRequest req, UUID performedBy) {
        ensureInputExists(inputId);
        if (MovementReason.isInternal(req.reason())) {
            throw new InvalidMovementException(
                    i18n.getMessage("input.movement.reason.task-not-allowed"));
        }
        UUID id = movementRepository.insert(
                inputId, req.kind(), req.quantity(), req.occurred_at(),
                null, performedBy, req.reason(), req.notes());
        stockAlertService.checkAndEmitLowStock(inputId);
        return id;
    }

    /**
     * Invocado por {@code TaskCompletedListener}: registra un OUT con
     * task_id y reason='TASK'. No es accesible por REST.
     */
    @Transactional
    public void registerConsumption(UUID inputId, java.math.BigDecimal quantity,
                                    UUID taskId, UUID performedBy, LocalDate occurredAt) {
        // No exigimos quantity > stock para no perder trazabilidad
        // ("advertir al usuario y permitir continuar" — §7.2).
        movementRepository.insert(
                inputId, MovementKind.OUT, quantity, occurredAt,
                taskId, performedBy, MovementReason.TASK, null);
        stockAlertService.checkAndEmitLowStock(inputId);
    }

    /**
     * Reversion administrativa: crea un IN de igual cantidad con
     * reason='TASK_REVERT'. Usado tras cancelar manualmente una task FINISHED.
     */
    @Transactional
    public UUID revertMovement(UUID movementId, UUID performedBy) {
        InputMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new MovementNotFoundException(
                        i18n.getMessage("input.movement.not.found")));
        if (original.kind() != MovementKind.OUT) {
            throw new InvalidMovementException(
                    i18n.getMessage("input.movement.reason.task-not-allowed"));
        }
        UUID id = movementRepository.insert(
                original.input_id(),
                MovementKind.IN,
                original.quantity(),
                LocalDate.now(),
                original.task_id(),
                performedBy,
                MovementReason.TASK_REVERT,
                "revert of movement " + movementId);
        stockAlertService.checkAndEmitLowStock(original.input_id());
        return id;
    }

    @Transactional(readOnly = true)
    public PageResponse<InputMovement> search(UUID inputId, MovementKind kind,
                                              Boolean taskIdNotNull,
                                              LocalDate from, LocalDate to,
                                              int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        int offset = safePage * safeSize;
        var items = movementRepository.search(inputId, kind, taskIdNotNull, from, to, offset, safeSize);
        long total = movementRepository.count(inputId, kind, taskIdNotNull, from, to);
        return new PageResponse<>(safePage, safeSize, total, items);
    }

    private void ensureInputExists(UUID inputId) {
        if (!inputRepository.existsByIdAndNotDeleted(inputId)) {
            throw new InputNotFoundException(i18n.getMessage("input.not.found"));
        }
    }
}
