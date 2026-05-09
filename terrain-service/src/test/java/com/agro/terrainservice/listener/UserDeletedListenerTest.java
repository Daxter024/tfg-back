package com.agro.terrainservice.listener;

import com.agro.terrainservice.event.UserDeletedEvent;
import com.agro.terrainservice.service.TerrainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios del listener Kafka. La integracion real con un broker
 * (EmbeddedKafka o Testcontainers) queda pendiente; aqui validamos la logica
 * de despacho del listener contra mocks.
 */
@ExtendWith(MockitoExtension.class)
class UserDeletedListenerTest {

    @Mock private TerrainService terrainService;
    @InjectMocks private UserDeletedListener listener;

    @Test
    @DisplayName("TER-11.02 unit - listener delega a deleteTerrainsByUserId")
    void handleUserDeleted_delegatesToService() {
        UUID uid = UUID.randomUUID();
        UserDeletedEvent event = new UserDeletedEvent(uid);
        doNothing().when(terrainService).deleteTerrainsByUserId(uid);

        listener.handleUserDeleted(event);

        verify(terrainService, times(1)).deleteTerrainsByUserId(uid);
    }

    @Test
    @DisplayName("TER-11.05 unit - listener con userId=null delega tal cual al service")
    void handleUserDeleted_passesNullUserId() {
        UserDeletedEvent event = new UserDeletedEvent(null);
        doNothing().when(terrainService).deleteTerrainsByUserId(null);

        listener.handleUserDeleted(event);

        verify(terrainService).deleteTerrainsByUserId(null);
    }

    @Test
    @DisplayName("TER-11.06 unit - llamadas repetidas con mismo evento delegan cada vez")
    void handleUserDeleted_idempotency_delegatesEachCall() {
        UUID uid = UUID.randomUUID();
        UserDeletedEvent event = new UserDeletedEvent(uid);
        doNothing().when(terrainService).deleteTerrainsByUserId(uid);

        listener.handleUserDeleted(event);
        listener.handleUserDeleted(event);

        verify(terrainService, times(2)).deleteTerrainsByUserId(uid);
    }

    @Test
    @DisplayName("TER-11.x - listener propaga RuntimeException del service")
    void handleUserDeleted_propagatesServiceException() {
        UUID uid = UUID.randomUUID();
        UserDeletedEvent event = new UserDeletedEvent(uid);
        doThrow(new RuntimeException("boom")).when(terrainService).deleteTerrainsByUserId(uid);

        try {
            listener.handleUserDeleted(event);
            org.junit.jupiter.api.Assertions.fail("Expected RuntimeException to propagate");
        } catch (RuntimeException expected) {
            // ok
        }
        verify(terrainService).deleteTerrainsByUserId(uid);
    }
}
