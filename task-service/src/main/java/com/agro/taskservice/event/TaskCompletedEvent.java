package com.agro.taskservice.event;

import com.agro.taskservice.dto.ConsumedInput;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado en el topic {@code task-completed} cuando una tarea pasa a
 * {@code FINISHED}. Consumido por input-service (HU-INS-02) y por la futura
 * extension de season-service (HU-CUL-02) para crear treatments.
 *
 * <p>Sin {@code parcelId} — main no tiene parcels. Si en el futuro se
 * reintroduce el concepto, se anade el campo sin breaking change (los
 * consumidores son tolerantes a campos extras).</p>
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
