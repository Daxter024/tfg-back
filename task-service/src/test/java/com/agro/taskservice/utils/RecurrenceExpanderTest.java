package com.agro.taskservice.utils;

import com.agro.taskservice.dto.RecurrenceSpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceExpanderTest {

    private final RecurrenceExpander expander = new RecurrenceExpander();

    @Test
    void weekly_x10_generates_10_children() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        // WEEKLY interval=1, until = start + 10 weeks; expander excludes the template itself
        var spec = new RecurrenceSpec(RecurrenceSpec.Frequency.WEEKLY, 1, start.toLocalDate().plusWeeks(10));
        List<LocalDateTime> out = expander.expand(start, spec);
        assertThat(out).hasSize(10);
        assertThat(out.get(0)).isEqualTo(start.plusWeeks(1));
        assertThat(out.get(9)).isEqualTo(start.plusWeeks(10));
    }

    @Test
    void daily_until_today_returns_empty() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        var spec = new RecurrenceSpec(RecurrenceSpec.Frequency.DAILY, 1, start.toLocalDate());
        assertThat(expander.expand(start, spec)).isEmpty();
    }

    @Test
    void monthly_respects_interval() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 10, 0);
        var spec = new RecurrenceSpec(RecurrenceSpec.Frequency.MONTHLY, 2, LocalDate.of(2026, 12, 31));
        List<LocalDateTime> out = expander.expand(start, spec);
        // Mar, May, Jul, Sep, Nov — 5 instances
        assertThat(out).hasSize(5);
        assertThat(out.get(0)).isEqualTo(LocalDateTime.of(2026, 3, 15, 10, 0));
        assertThat(out.get(4)).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
    }

    @Test
    void exceeding_365_signals_caller_to_abort() {
        // Daily for 2 years -> ~730 instances; we return more than 365 + 1 marker
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var spec = new RecurrenceSpec(RecurrenceSpec.Frequency.DAILY, 1, LocalDate.of(2028, 1, 1));
        List<LocalDateTime> out = expander.expand(start, spec);
        assertThat(out.size()).isGreaterThan(RecurrenceExpander.MAX_INSTANCES);
    }

    @Test
    void null_spec_returns_empty() {
        assertThat(expander.expand(LocalDateTime.now(), null)).isEmpty();
    }
}
