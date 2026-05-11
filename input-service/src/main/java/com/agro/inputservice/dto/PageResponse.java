package com.agro.inputservice.dto;

import java.util.List;

/**
 * Envoltorio paginado generico — mismo shape que el devuelto por otros
 * servicios del monorepo (page/size/total/items).
 */
public record PageResponse<T>(
        int page,
        int size,
        long total,
        List<T> items
) {
}
