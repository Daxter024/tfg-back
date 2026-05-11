package com.agro.inputservice.service;

import com.agro.inputservice.dto.InputRequest;
import com.agro.inputservice.dto.InputUpdateRequest;
import com.agro.inputservice.dto.PageResponse;
import com.agro.inputservice.exception.CategoryImmutableException;
import com.agro.inputservice.exception.InputNameDuplicatedException;
import com.agro.inputservice.exception.InputNotFoundException;
import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import com.agro.inputservice.repository.InputRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso CRUD del catalogo {@code input}. La logica de stock-low la
 * orquesta {@link com.agro.inputservice.service.StockAlertService} desde
 * {@link MovementService} (despues de cada movimiento), no aqui.
 */
@Service
@RequiredArgsConstructor
public class InputService {

    private final InputRepository repository;
    private final I18nService i18n;

    @Transactional(readOnly = true)
    public Input getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new InputNotFoundException(i18n.getMessage("input.not.found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<Input> search(InputCategory category, String q, boolean lowStockOnly,
                                      boolean includeDeleted, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        int offset = safePage * safeSize;
        var items = repository.search(category, q, lowStockOnly, includeDeleted, offset, safeSize);
        long total = repository.count(category, q, lowStockOnly, includeDeleted);
        return new PageResponse<>(safePage, safeSize, total, items);
    }

    @Transactional
    public UUID create(InputRequest req, UUID createdBy) {
        if (repository.existsByNameAndCategoryAlive(req.name(), req.category())) {
            throw new InputNameDuplicatedException(i18n.getMessage("input.name.duplicated"));
        }
        return repository.insert(
                req.name(),
                req.category(),
                req.unit(),
                req.low_stock_threshold(),
                req.supplier(),
                req.notes(),
                createdBy);
    }

    @Transactional
    public void update(UUID id, InputUpdateRequest req) {
        Input existing = repository.findById(id)
                .orElseThrow(() -> new InputNotFoundException(i18n.getMessage("input.not.found")));

        if (existing.deleted_at() != null) {
            throw new InputNotFoundException(i18n.getMessage("input.not.found"));
        }

        // Cambiar categoria solo si NO hay movimientos.
        if (req.category() != null && req.category() != existing.category()
                && repository.hasMovements(id)) {
            throw new CategoryImmutableException(i18n.getMessage("input.category.immutable"));
        }

        // Si se renombra (o cambia categoria), validar unicidad.
        String newName = req.name() != null ? req.name() : existing.name();
        InputCategory newCategory = req.category() != null ? req.category() : existing.category();
        if ((req.name() != null || req.category() != null)
                && repository.existsByNameAndCategoryAliveExcludingId(newName, newCategory, id)) {
            throw new InputNameDuplicatedException(i18n.getMessage("input.name.duplicated"));
        }

        boolean clearThreshold = Boolean.TRUE.equals(req.clear_threshold());
        int rows = repository.updatePartial(
                id,
                req.name(),
                req.category(),
                req.unit(),
                req.low_stock_threshold(),
                req.supplier(),
                req.notes(),
                clearThreshold);
        if (rows == 0) {
            throw new InputNotFoundException(i18n.getMessage("input.not.found"));
        }
    }

    @Transactional
    public void softDelete(UUID id) {
        int rows = repository.softDelete(id);
        if (rows == 0) {
            // Idempotente — si ya estaba borrado seguimos devolviendo 204
            // pero solo si la fila existe. Si no existe nunca, 404.
            if (repository.findById(id).isEmpty()) {
                throw new InputNotFoundException(i18n.getMessage("input.not.found"));
            }
        }
    }
}
