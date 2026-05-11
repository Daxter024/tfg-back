package com.agro.inputservice.service;

import com.agro.inputservice.dto.InputRequest;
import com.agro.inputservice.dto.InputUpdateRequest;
import com.agro.inputservice.exception.CategoryImmutableException;
import com.agro.inputservice.exception.InputNameDuplicatedException;
import com.agro.inputservice.exception.InputNotFoundException;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import com.agro.inputservice.repository.InputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InputServiceTest {

    @Mock InputRepository repository;
    @Mock I18nService i18n;

    @InjectMocks InputService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubI18n() {
        when(i18n.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(any(String.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_inserts_when_not_duplicated() {
        when(repository.existsByNameAndCategoryAlive("Urea 46%", InputCategory.fertilizante))
                .thenReturn(false);
        UUID newId = UUID.randomUUID();
        when(repository.insert(any(), any(), any(), any(), any(), any(), eq(userId)))
                .thenReturn(newId);

        var req = new InputRequest("Urea 46%", InputCategory.fertilizante, "kg",
                new BigDecimal("10.000"), "Acme", null);
        UUID result = service.create(req, userId);

        assertThat(result).isEqualTo(newId);
    }

    @Test
    void create_rejects_duplicate_alive() {
        when(repository.existsByNameAndCategoryAlive("Urea", InputCategory.fertilizante))
                .thenReturn(true);
        var req = new InputRequest("Urea", InputCategory.fertilizante, "kg", null, null, null);

        assertThatThrownBy(() -> service.create(req, userId))
                .isInstanceOf(InputNameDuplicatedException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getById_throws_when_missing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(InputNotFoundException.class);
    }

    @Test
    void update_rejects_category_change_when_has_movements() {
        UUID id = UUID.randomUUID();
        Input existing = new Input(id, "Urea", InputCategory.fertilizante, "kg",
                null, null, null, userId, Instant.now(), null, null, BigDecimal.ZERO);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.hasMovements(id)).thenReturn(true);

        InputUpdateRequest req = new InputUpdateRequest(null, InputCategory.semilla, null,
                null, null, null, null);
        assertThatThrownBy(() -> service.update(id, req))
                .isInstanceOf(CategoryImmutableException.class);
    }

    @Test
    void update_allows_category_change_when_no_movements() {
        UUID id = UUID.randomUUID();
        Input existing = new Input(id, "Urea", InputCategory.fertilizante, "kg",
                null, null, null, userId, Instant.now(), null, null, BigDecimal.ZERO);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.hasMovements(id)).thenReturn(false);
        when(repository.existsByNameAndCategoryAliveExcludingId("Urea", InputCategory.semilla, id))
                .thenReturn(false);
        when(repository.updatePartial(eq(id), any(), eq(InputCategory.semilla), any(),
                any(), any(), any(), eq(false))).thenReturn(1);

        InputUpdateRequest req = new InputUpdateRequest(null, InputCategory.semilla, null,
                null, null, null, null);
        service.update(id, req);

        verify(repository).updatePartial(eq(id), eq(null), eq(InputCategory.semilla),
                eq(null), eq(null), eq(null), eq(null), eq(false));
    }

    @Test
    void softDelete_throws_when_input_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(repository.softDelete(id)).thenReturn(0);
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(id))
                .isInstanceOf(InputNotFoundException.class);
    }

    @Test
    void softDelete_idempotent_when_already_deleted() {
        UUID id = UUID.randomUUID();
        Input deleted = new Input(id, "Urea", InputCategory.fertilizante, "kg",
                null, null, null, userId, Instant.now(), null, Instant.now(), BigDecimal.ZERO);
        when(repository.softDelete(id)).thenReturn(0);
        when(repository.findById(id)).thenReturn(Optional.of(deleted));
        // No throw
        service.softDelete(id);
    }

    @Test
    void search_returns_paginated_response() {
        UUID id = UUID.randomUUID();
        Input row = new Input(id, "Urea", InputCategory.fertilizante, "kg",
                null, null, null, userId, Instant.now(), null, null, BigDecimal.TEN);
        when(repository.search(null, null, false, false, 0, 20))
                .thenReturn(List.of(row));
        when(repository.count(null, null, false, false)).thenReturn(1L);

        var page = service.search(null, null, false, false, 0, 20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
    }
}
