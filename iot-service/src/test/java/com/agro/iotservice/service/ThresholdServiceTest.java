package com.agro.iotservice.service;

import com.agro.iotservice.client.UserGrpcClient;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.ThresholdRequest;
import com.agro.iotservice.exception.InvalidThresholdException;
import com.agro.iotservice.repository.ThresholdRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThresholdServiceTest {

    @Mock ThresholdRepository repository;
    @Mock UserGrpcClient userClient;
    @Mock I18nService i18n;

    @InjectMocks ThresholdService service;

    @Test
    void create_bothSensorAndVariable_throws() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        ThresholdRequest req = new ThresholdRequest(UUID.randomUUID(),
                VariableKind.temperature, new BigDecimal("5"), null, List.of());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("threshold.target.xor");
    }

    @Test
    void create_neitherSensorNorVariable_throws() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        ThresholdRequest req = new ThresholdRequest(null, null,
                new BigDecimal("5"), new BigDecimal("10"), List.of());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidThresholdException.class);
    }

    @Test
    void create_noBounds_throws() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        ThresholdRequest req = new ThresholdRequest(UUID.randomUUID(), null,
                null, null, List.of());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("threshold.bounds.meaningful");
    }

    @Test
    void create_minGreaterThanMax_throws() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        ThresholdRequest req = new ThresholdRequest(UUID.randomUUID(), null,
                new BigDecimal("30"), new BigDecimal("10"), List.of());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("threshold.bounds.ordered");
    }

    @Test
    void create_notifyUserDoesNotExist_throws() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID badUser = UUID.randomUUID();
        when(userClient.validateUser(badUser)).thenReturn(false);
        ThresholdRequest req = new ThresholdRequest(UUID.randomUUID(), null,
                new BigDecimal("5"), null, List.of(badUser));
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("threshold.notify.user.not.found");
    }

    @Test
    void create_valid_persists() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID notify = UUID.randomUUID();
        UUID sensorId = UUID.randomUUID();
        when(userClient.validateUser(notify)).thenReturn(true);
        UUID newId = UUID.randomUUID();
        when(repository.insert(any(), any(), any(), any(), any())).thenReturn(newId);

        ThresholdRequest req = new ThresholdRequest(sensorId, null,
                new BigDecimal("5"), new BigDecimal("10"), List.of(notify));
        UUID id = service.create(req);

        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(newId);
    }
}
