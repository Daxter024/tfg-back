package com.agro.seasonservice.listener;

import com.agro.seasonservice.event.TerrainDeletedEvent;
import com.agro.seasonservice.service.SeasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TerrainDeletedListener {

    private final SeasonService seasonService;

    @KafkaListener(topics = "terrain-deleted", groupId = "season-service-group")
    public void handleTerrainDeleted(TerrainDeletedEvent event) {
        log.info("Received TerrainDeletedEvent: {}", event);
        seasonService.deleteSeasonsByTerrainId(event.terrainId());
    }
}
