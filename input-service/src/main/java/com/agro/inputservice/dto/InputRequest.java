package com.agro.inputservice.dto;

import com.agro.inputservice.model.InputCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload de creacion de un insumo. {@code created_by} se infiere de la
 * cabecera X-User-Id, no del body.
 */
public record InputRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotNull InputCategory category,
        @NotBlank @Size(max = 16) String unit,
        @DecimalMin("0.0") BigDecimal low_stock_threshold,
        @Size(max = 200) String supplier,
        String notes
) {
}
