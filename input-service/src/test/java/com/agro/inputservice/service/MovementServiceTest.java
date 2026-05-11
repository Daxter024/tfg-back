package com.agro.inputservice.service;

import com.agro.inputservice.constants.MovementReason;
import com.agro.inputservice.dto.MovementRequest;
import com.agro.inputservice.exception.InputNotFoundException;
import com.agro.inputservice.exception.InvalidMovementException;
import com.agro.inputservice.exception.MovementNotFoundException;
import com.agro.inputservice.model.InputMovement;
import com.agro.inputservice.model.MovementKind;
import com.agro.inputservice.repository.InputRepository;
import com.agro.inputservice.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovementServiceTest {

    @Mock MovementRepository movementRepository;
    @Mock InputRepository inputRepository;
    @Mock StockAlertService stockAlertService;
    @Mock I18nService i18n;

    @InjectMocks MovementService service;

    private final UUID inputId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubI18n() {
        when(i18n.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(any(String.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordManualMovement_inserts_and_checks_low_stock() {
        when(inputRepository.existsByIdAndNotDeleted(inputId)).thenReturn(true);
        UUID movId = UUID.randomUUID();
        when(movementRepository.insert(eq(inputId), eq(MovementKind.IN), any(), any(),
                eq(null), eq(userId), eq(MovementReason.PURCHASE), any())).thenReturn(movId);

        var req = new MovementRequest(MovementKind.IN, new BigDecimal("5"),
                LocalDate.now(), MovementReason.PURCHASE, null);
        UUID result = service.recordManualMovement(inputId, req, userId);

        assertThat(result).isEqualTo(movId);
        verify(stockAlertService).checkAndEmitLowStock(inputId);
    }

    @Test
    void recordManualMovement_rejects_TASK_reason() {
        when(inputRepository.existsByIdAndNotDeleted(inputId)).thenReturn(true);
        var req = new MovementRequest(MovementKind.OUT, new BigDecimal("1"),
                LocalDate.now(), MovementReason.TASK, null);
        assertThatThrownBy(() -> service.recordManualMovement(inputId, req, userId))
                .isInstanceOf(InvalidMovementException.class);
        verify(movementRepository, never()).insert(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void recordManualMovement_throws_when_input_missing() {
        when(inputRepository.existsByIdAndNotDeleted(inputId)).thenReturn(false);
        var req = new MovementRequest(MovementKind.IN, new BigDecimal("1"),
                LocalDate.now(), MovementReason.PURCHASE, null);
        assertThatThrownBy(() -> service.recordManualMovement(inputId, req, userId))
                .isInstanceOf(InputNotFoundException.class);
    }

    @Test
    void registerConsumption_inserts_OUT_with_taskId() {
        UUID taskId = UUID.randomUUID();
        when(movementRepository.insert(eq(inputId), eq(MovementKind.OUT),
                eq(new BigDecimal("3")), any(),
                eq(taskId), eq(userId), eq(MovementReason.TASK), eq(null)))
                .thenReturn(UUID.randomUUID());

        service.registerConsumption(inputId, new BigDecimal("3"), taskId, userId,
                LocalDate.of(2026, 5, 1));

        verify(movementRepository).insert(eq(inputId), eq(MovementKind.OUT),
                eq(new BigDecimal("3")), eq(LocalDate.of(2026, 5, 1)),
                eq(taskId), eq(userId), eq(MovementReason.TASK), eq(null));
        verify(stockAlertService).checkAndEmitLowStock(inputId);
    }

    @Test
    void revertMovement_creates_IN_of_same_quantity() {
        UUID movId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        InputMovement out = new InputMovement(
                movId, inputId, MovementKind.OUT, new BigDecimal("7"),
                LocalDate.now(), taskId, userId, MovementReason.TASK, null, Instant.now());
        when(movementRepository.findById(movId)).thenReturn(Optional.of(out));
        UUID newId = UUID.randomUUID();
        when(movementRepository.insert(eq(inputId), eq(MovementKind.IN),
                eq(new BigDecimal("7")), any(),
                eq(taskId), eq(userId), eq(MovementReason.TASK_REVERT), any()))
                .thenReturn(newId);

        UUID result = service.revertMovement(movId, userId);
        assertThat(result).isEqualTo(newId);
        verify(stockAlertService).checkAndEmitLowStock(inputId);
    }

    @Test
    void revertMovement_throws_when_movement_missing() {
        UUID movId = UUID.randomUUID();
        when(movementRepository.findById(movId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revertMovement(movId, userId))
                .isInstanceOf(MovementNotFoundException.class);
    }

    @Test
    void revertMovement_rejects_IN_movement() {
        UUID movId = UUID.randomUUID();
        InputMovement in = new InputMovement(
                movId, inputId, MovementKind.IN, new BigDecimal("7"),
                LocalDate.now(), null, userId, MovementReason.PURCHASE, null, Instant.now());
        when(movementRepository.findById(movId)).thenReturn(Optional.of(in));
        assertThatThrownBy(() -> service.revertMovement(movId, userId))
                .isInstanceOf(InvalidMovementException.class);
    }
}
