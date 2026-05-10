package com.agro.seasonservice.listener;

import com.agro.seasonservice.event.TerrainDeletedEvent;
import com.agro.seasonservice.service.SeasonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TerrainDeletedListenerTest {

    @Mock
    private SeasonService seasonService;

    @InjectMocks
    private TerrainDeletedListener listener;

    @Test
    void handleTerrainDeleted_callsDeleteByTerrainId() {
        UUID terrainId = UUID.randomUUID();
        TerrainDeletedEvent event = new TerrainDeletedEvent(terrainId);

        listener.handleTerrainDeleted(event);

        verify(seasonService).deleteSeasonsByTerrainId(terrainId);
    }
}
