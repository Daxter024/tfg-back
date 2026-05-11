package com.agro.inputservice.event;

import com.agro.inputservice.dto.ConsumedInput;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Evento {@code task-completed} consumido de task-service. El type-mapping en
 * {@code application.properties} traduce
 * {@code com.agro.taskservice.event.TaskCompletedEvent} a este tipo.
 *
 * <p>Payload sin sub-divisiones del terreno — alineado con el productor
 * actual de task-service: solo {@code terrainId} como referencia geografica.</p>
 */
public record TaskCompletedEvent(
        UUID taskId,
        String taskTypeCode,
        UUID terrainId,
        UUID performedBy,
        LocalDateTime finishedAt,
        List<ConsumedInput> consumedInputs
) {
}
