package com.agro.taskservice.listener;

import com.agro.taskservice.event.UserDeletedEvent;
import com.agro.taskservice.service.NotificationService;
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
class UserDeletedListenerTest {

    @Mock TaskService taskService;
    @Mock NotificationService notificationService;
    @InjectMocks UserDeletedListener listener;

    @Test
    void onUserDeleted_invokesPolicyAndNotificationCleanup() {
        UUID user = UUID.randomUUID();
        when(taskService.handleUserDeleted(user))
                .thenReturn(new TaskService.UserDeletedSummary(3, 1, 0));

        listener.onUserDeleted(new UserDeletedEvent(user));

        verify(taskService, times(1)).handleUserDeleted(user);
        verify(notificationService, times(1)).deleteByUserId(user);
    }
}
