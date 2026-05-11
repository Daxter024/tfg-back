package com.agro.taskservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record RecurrenceSpec(
        @NotNull(message = "{task.recurrence.frequency.required}")
        Frequency frequency,
        @Positive(message = "{task.recurrence.interval.positive}")
        Integer interval,
        @NotNull(message = "{task.recurrence.until.required}")
        LocalDate until
) {
    public enum Frequency {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
