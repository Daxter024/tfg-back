package com.agro.taskservice.listener;

import com.agro.taskservice.event.UserDeletedEvent;
import com.agro.taskservice.service.NotificationService;
import com.agro.taskservice.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Politica D2 (ver §10.1 del plan): al recibir {@code user-deleted},
 * borrar/anonymizar tareas + borrar notifs+prefs del user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedListener {

    private final TaskService taskService;
    private final NotificationService notificationService;

    @KafkaListener(topics = "user-deleted", groupId = "task-service-group")
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("user-deleted received: {}", event);
        var summary = taskService.handleUserDeleted(event.userId());
        notificationService.deleteByUserId(event.userId());
        log.info("user-deleted {} -> deleted={} anonAssignee={} anonCreator={}",
                event.userId(), summary.deleted(), summary.anonAssignee(), summary.anonCreator());
    }
}
