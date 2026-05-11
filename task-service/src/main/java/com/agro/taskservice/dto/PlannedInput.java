package com.agro.taskservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedInput(
        @NotBlank(message = "{task.planned.input.name.required}")
        String input_name,
        @Positive(message = "{task.planned.input.quantity.positive}")
        BigDecimal quantity,
        @NotBlank(message = "{task.planned.input.unit.required}")
        String unit,
        UUID input_id
) {
}
