package com.agro.taskservice.dto;

import java.util.List;

/**
 * Wrapper de paginacion ligero. Evitamos importar Spring Data (no usamos JPA)
 * y exponemos un payload simple y estable hacia el frontend.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
