package com.agro.taskservice.listener;

import com.agro.taskservice.event.TerrainDeletedEvent;
import com.agro.taskservice.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerrainDeletedListenerTest {

    @Mock TaskService taskService;
    @InjectMocks TerrainDeletedListener listener;

    @Test
    void onTerrainDeleted_cascades() {
        UUID terrain = UUID.randomUUID();
        when(taskService.deleteByTerrainId(terrain)).thenReturn(7);

        listener.onTerrainDeleted(new TerrainDeletedEvent(terrain));

        verify(taskService, times(1)).deleteByTerrainId(terrain);
    }
}
