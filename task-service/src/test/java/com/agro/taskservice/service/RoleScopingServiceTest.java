package com.agro.taskservice.service;

import com.agro.taskservice.client.TerrainHttpClient;
import com.agro.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleScopingServiceTest {

    @Mock TerrainHttpClient terrainHttpClient;
    @InjectMocks RoleScopingService scoping;

    @Test
    void admin_doesNotApplyScope() {
        UUID user = UUID.randomUUID();
        var base = new TaskRepository.TaskFilters(null, null, null, null,
                null, null, null, null, null);

        var out = scoping.scope(base, user, "administrador");
        assertThat(out.terrainIdIn()).isNull();
        assertThat(out.assignedTo()).isNull();
        verify(terrainHttpClient, never()).findTerrainIdsByUser(any());
    }

    @Test
    void agricultor_appliesTerrainIdInFromRest() {
        UUID user = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(terrainHttpClient.findTerrainIdsByUser(user)).thenReturn(List.of(t1, t2));

        var out = scoping.scope(null, user, "agricultor");
        assertThat(out.terrainIdIn()).containsExactlyInAnyOrder(t1, t2);
        assertThat(out.assignedTo()).isNull();
    }

    @Test
    void tecnico_appliesAssigneeFilter() {
        UUID user = UUID.randomUUID();
        var out = scoping.scope(null, user, "tecnico");
        assertThat(out.assignedTo()).isEqualTo(user);
        assertThat(out.terrainIdIn()).isNull();
    }

    @Test
    void unknownRole_fallsBackToAssignee() {
        UUID user = UUID.randomUUID();
        var out = scoping.scope(null, user, "anything");
        assertThat(out.assignedTo()).isEqualTo(user);
    }

    private static UUID any() {
        return org.mockito.ArgumentMatchers.any(UUID.class);
    }
}
