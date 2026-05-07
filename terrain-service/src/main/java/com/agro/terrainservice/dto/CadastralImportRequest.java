package com.agro.terrainservice.dto;

import com.agro.terrainservice.constants.ReferenceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * HU-TER-05: cuerpo de la peticion de importacion desde Catastro / SIGPAC.
 * El service valida el formato sintactico de {@code reference} antes de llamar
 * a la API externa.
 */
public record CadastralImportRequest(
        @NotBlank(message = "{cadastral.reference.malformed}") String reference,
        @NotNull ReferenceKind kind
) {
}
