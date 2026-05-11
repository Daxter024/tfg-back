package com.agro.inputservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Insumo. El campo {@code current_stock} no es columna sino agregado calculado
 * por la vista {@code input_with_stock} y los queries del repositorio.
 */
public record Input(
        UUID id,
        String name,
        InputCategory category,
        String unit,
        BigDecimal low_stock_threshold,
        String supplier,
        String notes,
        UUID created_by,
        Instant created_at,
        Instant updated_at,
        Instant deleted_at,
        BigDecimal current_stock
) {
}
