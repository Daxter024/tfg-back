package com.agro.taskservice.dto;

import com.agro.taskservice.constants.TaskState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record TaskStateTransitionRequest(
        @NotNull(message = "{task.transition.state.required}")
        TaskState to_state,
        LocalDateTime effective_at,
        @Positive(message = "{task.duration.positive}")
        Integer real_duration_minutes,
        @Valid
        List<ConsumedInput> consumed_inputs,
        String note
) {
}
