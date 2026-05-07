package com.agro.terrainservice.service;

import com.agro.terrainservice.event.ParcelDeletedEvent;
import com.agro.terrainservice.event.TerrainDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTerrainDeleted(TerrainDeletedEvent event) {
        log.info("Publishing TerrainDeletedEvent: {}", event);
        kafkaTemplate.send("terrain-deleted", event.terrainId().toString(), event);
    }

    /** HU-TER-04: notifica el borrado de una parcela a los servicios downstream. */
    public void publishParcelDeleted(ParcelDeletedEvent event) {
        log.info("Publishing ParcelDeletedEvent: {}", event);
        kafkaTemplate.send("parcel-deleted", event.parcelId().toString(), event);
    }
}
