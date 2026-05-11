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
 * <p>Payload SIN parcelId — alineado con el productor actual de task-service
 * (sin sub-divisiones del terreno).</p>
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
