package com.agro.terrainservice.service;

import com.agro.terrainservice.event.TerrainDeletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Test unitario del publicador Kafka. La integracion contra un broker real
 * (Testcontainers o EmbeddedKafka) queda pendiente.
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Mock private KafkaTemplate kafkaTemplate;

    @InjectMocks private EventPublisher eventPublisher;

    @Test
    @DisplayName("TER-3.01 unit - publishTerrainDeleted envia al topic terrain-deleted con la key correcta")
    void publishTerrainDeleted_sendsToCorrectTopic() {
        UUID id = UUID.randomUUID();
        TerrainDeletedEvent event = new TerrainDeletedEvent(id);

        eventPublisher.publishTerrainDeleted(event);

        verify(kafkaTemplate).send(eq("terrain-deleted"), eq(id.toString()), any(TerrainDeletedEvent.class));
    }
}
