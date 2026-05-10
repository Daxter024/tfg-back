package com.agro.seasonservice.listener;

import com.agro.seasonservice.event.TerrainDeletedEvent;
import com.agro.seasonservice.service.SeasonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TerrainDeletedListenerTest {

    @Mock
    private SeasonService seasonService;

    @InjectMocks
    private TerrainDeletedListener listener;

    @Test
    @DisplayName("SEASON-6.01: handleTerrainDeleted delega al service")
    void handleTerrainDeleted_callsDeleteByTerrainId() {
        UUID terrainId = UUID.randomUUID();
        TerrainDeletedEvent event = new TerrainDeletedEvent(terrainId);

        listener.handleTerrainDeleted(event);

        verify(seasonService).deleteSeasonsByTerrainId(terrainId);
    }

    @Test
    @DisplayName("SEASON-6.02: evento con terrainId nulo se delega tal cual (idempotente en BBDD)")
    void handleTerrainDeleted_nullTerrainId_delegatesAnyway() {
        TerrainDeletedEvent event = new TerrainDeletedEvent(null);

        // El listener no valida; delega al service. El SQL terminará borrando 0 filas.
        listener.handleTerrainDeleted(event);

        verify(seasonService).deleteSeasonsByTerrainId(null);
    }

    @Test
    @DisplayName("SEASON-6.03: si el service lanza, el listener propaga (Spring Kafka logueará y reintentará)")
    void handleTerrainDeleted_serviceThrows_propagates() {
        UUID terrainId = UUID.randomUUID();
        TerrainDeletedEvent event = new TerrainDeletedEvent(terrainId);
        doThrow(new RuntimeException("DB down"))
                .when(seasonService).deleteSeasonsByTerrainId(terrainId);

        assertThatThrownBy(() -> listener.handleTerrainDeleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }
}
