package com.agro.terrainservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CadastralImportRequest(
        @NotBlank(message = "{cadastral.reference.required}") String reference,
        @NotNull(message = "{cadastral.kind.required}") ReferenceKind kind
) {
    public enum ReferenceKind {
        CADASTRAL,
        SIGPAC
    }
}
