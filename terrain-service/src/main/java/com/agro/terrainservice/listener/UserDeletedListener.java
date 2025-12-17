package com.agro.terrainservice.listener;

import com.agro.terrainservice.event.UserDeletedEvent;
import com.agro.terrainservice.service.TerrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedListener {

    private final TerrainService terrainService;

    @KafkaListener(topics = "user-deleted", groupId = "terrain-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received UserDeletedEvent: {}", event);
        terrainService.deleteTerrainsByUserId(event.userId());
    }
}
