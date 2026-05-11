package com.agro.iotservice.listener;

import com.agro.iotservice.event.TerrainDeletedEvent;
import com.agro.iotservice.service.SensorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerrainDeletedListenerTest {

    @Mock SensorService sensorService;

    @InjectMocks TerrainDeletedListener listener;

    @Test
    void onTerrainDeleted_delegatesToService() {
        UUID terrainId = UUID.randomUUID();
        when(sensorService.deleteByTerrainId(terrainId)).thenReturn(4);

        listener.handleTerrainDeleted(new TerrainDeletedEvent(terrainId));

        verify(sensorService).deleteByTerrainId(terrainId);
    }
}
