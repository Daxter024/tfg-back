package com.agro.inputservice.dto;

import com.agro.inputservice.model.InputCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload PATCH — todos los campos opcionales. Cambiar {@code category} solo
 * se permite si el insumo no tiene movimientos; el service lo valida.
 *
 * <p>{@code clear_threshold=true} fuerza a poner el threshold a NULL (no se
 * puede expresar con un campo nullable porque "no enviado" y "null explicito"
 * se confunden).</p>
 */
public record InputUpdateRequest(
        @Size(min = 2, max = 200) String name,
        InputCategory category,
        @Size(max = 16) String unit,
        @DecimalMin("0.0") BigDecimal low_stock_threshold,
        Boolean clear_threshold,
        @Size(max = 200) String supplier,
        String notes
) {
}
