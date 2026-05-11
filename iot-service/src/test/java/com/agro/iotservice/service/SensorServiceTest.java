package com.agro.iotservice.service;

import com.agro.iotservice.client.TerrainGrpcClient;
import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.SensorRequest;
import com.agro.iotservice.exception.SensorNotFoundException;
import com.agro.iotservice.exception.TerrainNotFoundException;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.repository.SensorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensorServiceTest {

    @Mock SensorRepository repository;
    @Mock TerrainGrpcClient terrainClient;
    @Mock I18nService i18n;

    @InjectMocks SensorService service;

    @Test
    void create_terrainOK_persistsWithDefaultInterval() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainClient.checkTerrainExists(any())).thenReturn(true);
        UUID terrainId = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        when(repository.insert(any(), any(), any(), any(), anyInt(), any())).thenReturn(newId);

        SensorRequest req = new SensorRequest("hw-1", VariableKind.temperature, "C", terrainId, null);
        UUID id = service.create(req, creator);

        assertThat(id).isEqualTo(newId);
        verify(repository).insert("hw-1", VariableKind.temperature, "C", terrainId, 300, creator);
    }

    @Test
    void create_terrainMissing_throws404() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainClient.checkTerrainExists(any())).thenReturn(false);
        SensorRequest req = new SensorRequest(null, VariableKind.humidity, "%", UUID.randomUUID(), 120);
        assertThatThrownBy(() -> service.create(req, UUID.randomUUID()))
                .isInstanceOf(TerrainNotFoundException.class);
    }

    @Test
    void getById_missing_throws404() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(SensorNotFoundException.class);
    }

    @Test
    void getById_populatesLastValue() {
        UUID id = UUID.randomUUID();
        Sensor base = new Sensor(id, null, VariableKind.temperature, "C",
                UUID.randomUUID(), 300, SensorStatus.active,
                UUID.randomUUID(), Instant.now(), null, null, null);
        when(repository.findById(id)).thenReturn(Optional.of(base));
        when(repository.findLastValue(id)).thenReturn(Optional.of(new java.math.BigDecimal("21.7")));

        Sensor full = service.getById(id);
        assertThat(full.last_value()).isEqualByComparingTo("21.7");
    }
}
