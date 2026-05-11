package com.agro.iotservice.listener;

import com.agro.iotservice.event.TerrainDeletedEvent;
import com.agro.iotservice.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to terrain-deleted by removing every sensor on the terrain. The
 * ON DELETE CASCADE on sensor_reading / sensor_alert / device_api_key
 * cleans the descendants automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TerrainDeletedListener {

    private final SensorService sensorService;

    @KafkaListener(topics = "terrain-deleted", groupId = "iot-service-group")
    public void handleTerrainDeleted(TerrainDeletedEvent event) {
        int removed = sensorService.deleteByTerrainId(event.terrainId());
        log.info("terrain-deleted {} -> removed {} sensors", event.terrainId(), removed);
    }
}
