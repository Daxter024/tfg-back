package com.agro.taskservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;
import java.util.Map;

public record NotificationPreferenceDTO(
        @NotNull(message = "{task.notification.email.required}")
        Boolean email_enabled,
        @NotNull(message = "{task.notification.in_app.required}")
        Boolean in_app_enabled,
        @PositiveOrZero(message = "{task.notification.default.lead.positive}")
        Integer default_lead_minutes,
        Map<String, Integer> task_type_lead_minutes,
        LocalTime quiet_hours_start,
        LocalTime quiet_hours_end,
        Boolean also_notify_creator
) {
}
