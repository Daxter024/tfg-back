package com.agro.taskservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slot denso para vista de calendario (FullCalendar / Mantine Calendar).
 * {@code color_hint} es un nombre simbolico — el front decide el codigo de
 * color exacto.
 */
public record TaskCalendarSlotDTO(
        UUID id,
        String title,
        LocalDateTime planned_at,
        Integer estimated_duration_minutes,
        String state,
        String color_hint
) {
}
