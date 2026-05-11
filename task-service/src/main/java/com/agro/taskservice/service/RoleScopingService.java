package com.agro.taskservice.service;

import com.agro.taskservice.client.TerrainHttpClient;
import com.agro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HU-TAR-03 §8.3 — aplica el alcance por rol a los {@link
 * TaskRepository.TaskFilters} antes de listar/dashboard/export.
 *
 * <ul>
 *   <li>{@code administrador}: ve todo (no aplica filtro adicional).</li>
 *   <li>{@code agricultor}: solo tareas en sus terrenos — consulta a
 *       terrain-service por REST y aplica {@code terrainIdIn}.</li>
 *   <li>{@code tecnico}: solo {@code assigned_to = jwt.userId}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RoleScopingService {

    private final TerrainHttpClient terrainHttpClient;

    public TaskRepository.TaskFilters scope(TaskRepository.TaskFilters base,
                                            UUID userId, String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (base == null) {
            base = new TaskRepository.TaskFilters(null, null, null, null,
                    null, null, null, null, null);
        }
        return switch (r) {
            case "administrador", "admin" -> base;
            case "agricultor" -> withTerrainScope(base, userId);
            case "tecnico" -> withAssigneeScope(base, userId);
            default -> withAssigneeScope(base, userId);
        };
    }

    private TaskRepository.TaskFilters withTerrainScope(TaskRepository.TaskFilters base, UUID userId) {
        List<UUID> ids = terrainHttpClient.findTerrainIdsByUser(userId);
        return new TaskRepository.TaskFilters(
                base.assignedTo(), base.createdBy(), base.states(),
                base.typeCodes(), base.terrainId(), base.from(), base.to(),
                base.overdue(), ids);
    }

    private TaskRepository.TaskFilters withAssigneeScope(TaskRepository.TaskFilters base, UUID userId) {
        return new TaskRepository.TaskFilters(
                userId, base.createdBy(), base.states(),
                base.typeCodes(), base.terrainId(), base.from(), base.to(),
                base.overdue(), base.terrainIdIn());
    }
}
