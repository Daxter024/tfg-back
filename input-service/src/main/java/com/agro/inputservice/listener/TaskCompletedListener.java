package com.agro.inputservice.listener;

import com.agro.inputservice.dto.ConsumedInput;
import com.agro.inputservice.event.TaskCompletedEvent;
import com.agro.inputservice.service.MovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * HU-INS-02: por cada tarea FINISHED en task-service, crea un movimiento OUT
 * en el stock por cada {@link ConsumedInput} con {@code input_id} no nulo.
 *
 * <p>Si {@code input_id} es null el insumo era un campo libre — no entra en
 * stock. Si {@code consumedInputs} es null tampoco hacemos nada.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedListener {

    private final MovementService movementService;

    @KafkaListener(topics = "task-completed", groupId = "input-service-group")
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.info("task-completed received: task={} consumed={}",
                event.taskId(),
                event.consumedInputs() == null ? 0 : event.consumedInputs().size());
        if (event.consumedInputs() == null) {
            return;
        }
        for (ConsumedInput ci : event.consumedInputs()) {
            if (ci.input_id() == null) {
                // Entrada libre — no entra en stock.
                continue;
            }
            try {
                movementService.registerConsumption(
                        ci.input_id(),
                        ci.quantity(),
                        event.taskId(),
                        event.performedBy(),
                        event.finishedAt() != null ? event.finishedAt().toLocalDate() : null);
            } catch (Exception e) {
                // Un error en un input concreto no debe romper el resto del
                // payload (al-least-once garantiza reintentos del lote completo
                // si fuera transient — pero quedarse a medias bloquearia el
                // listener). Log + continuamos.
                log.warn("registerConsumption failed for input={} task={} — {}",
                        ci.input_id(), event.taskId(), e.getMessage());
            }
        }
    }
}
