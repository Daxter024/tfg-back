package com.agro.taskservice.listener;

import com.agro.taskservice.event.TerrainDeletedEvent;
import com.agro.taskservice.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Borrado en cascada del lado de task-service. Al desaparecer el terreno se
 * pierde el contexto agronomico de la tarea — politica explicita §10.2:
 * NO anonymizar, simplemente borrar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TerrainDeletedListener {

    private final TaskService taskService;

    @KafkaListener(topics = "terrain-deleted", groupId = "task-service-group")
    public void onTerrainDeleted(TerrainDeletedEvent event) {
        int removed = taskService.deleteByTerrainId(event.terrainId());
        log.info("terrain-deleted {} -> removed {} tasks", event.terrainId(), removed);
    }
}
