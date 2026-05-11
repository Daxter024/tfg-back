package com.agro.inputservice.listener;

import com.agro.inputservice.dto.ConsumedInput;
import com.agro.inputservice.event.TaskCompletedEvent;
import com.agro.inputservice.service.MovementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskCompletedListenerTest {

    @Mock MovementService movementService;
    @InjectMocks TaskCompletedListener listener;

    @Test
    void registers_OUT_when_input_id_present() {
        UUID inputId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 1, 12, 0);
        TaskCompletedEvent event = new TaskCompletedEvent(
                taskId, "FERTILIZATION", UUID.randomUUID(), performedBy, finishedAt,
                List.of(new ConsumedInput("Urea", inputId, new BigDecimal("4"), "kg")));

        listener.onTaskCompleted(event);

        verify(movementService).registerConsumption(
                eq(inputId), eq(new BigDecimal("4")),
                eq(taskId), eq(performedBy), eq(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void does_nothing_when_input_id_null() {
        TaskCompletedEvent event = new TaskCompletedEvent(
                UUID.randomUUID(), "OTHER", UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(),
                List.of(new ConsumedInput("entrada libre", null, new BigDecimal("1"), "kg")));

        listener.onTaskCompleted(event);

        verify(movementService, never()).registerConsumption(any(), any(), any(), any(), any());
    }

    @Test
    void does_nothing_when_consumedInputs_null() {
        TaskCompletedEvent event = new TaskCompletedEvent(
                UUID.randomUUID(), "OTHER", UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), null);

        listener.onTaskCompleted(event);

        verify(movementService, never()).registerConsumption(any(), any(), any(), any(), any());
    }

    @Test
    void registers_each_consumed_input_independently() {
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        TaskCompletedEvent event = new TaskCompletedEvent(
                t, "TREATMENT", UUID.randomUUID(), p, LocalDateTime.now(),
                List.of(
                        new ConsumedInput("A", UUID.randomUUID(), new BigDecimal("1"), "kg"),
                        new ConsumedInput("B-libre", null, new BigDecimal("2"), "L"),
                        new ConsumedInput("C", UUID.randomUUID(), new BigDecimal("3"), "kg")
                ));
        listener.onTaskCompleted(event);
        verify(movementService, times(2)).registerConsumption(any(), any(), any(), any(), any());
    }

    @Test
    void one_failed_input_does_not_break_others() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID t = UUID.randomUUID();
        TaskCompletedEvent event = new TaskCompletedEvent(
                t, "OTHER", UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now(),
                List.of(
                        new ConsumedInput("A", a, new BigDecimal("1"), "kg"),
                        new ConsumedInput("B", b, new BigDecimal("2"), "kg")
                ));
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(movementService).registerConsumption(eq(a), any(), any(), any(), any());
        listener.onTaskCompleted(event);
        // B se procesa aunque A haya petado
        verify(movementService).registerConsumption(eq(b), any(), any(), any(), any());
    }
}
