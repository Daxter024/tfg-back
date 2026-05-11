package com.agro.taskservice.service;

import com.agro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-TAR-03 — totales por estado, agrupacion semanal y por tipo. Reusa los
 * filtros de la listado (incluido el {@code terrainIdIn} que aplica el role
 * scoping del agricultor).
 */
@Service
@RequiredArgsConstructor
public class TaskDashboardService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(TaskRepository.TaskFilters filters) {
        Map<String, Long> totals = taskRepository.totalsByState(filters);
        List<Map<String, Object>> byWeek = taskRepository.countsByWeek(filters);
        List<Map<String, Object>> byType = taskRepository.countsByType(filters);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", totals);
        out.put("by_week", byWeek);
        out.put("by_type", byType);
        return out;
    }
}
