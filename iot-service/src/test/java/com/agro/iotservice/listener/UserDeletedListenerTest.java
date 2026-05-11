package com.agro.iotservice.listener;

import com.agro.iotservice.event.UserDeletedEvent;
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
class UserDeletedListenerTest {

    @Mock SensorService sensorService;

    @InjectMocks UserDeletedListener listener;

    @Test
    void onUserDeleted_delegatesToService() {
        UUID userId = UUID.randomUUID();
        when(sensorService.deleteByCreatedBy(userId)).thenReturn(2);

        listener.handleUserDeleted(new UserDeletedEvent(userId));

        verify(sensorService).deleteByCreatedBy(userId);
    }
}
