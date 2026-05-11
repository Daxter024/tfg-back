package com.agro.iotservice.service;

import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.exception.SensorNotFoundException;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.repository.SensorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceKeyServiceTest {

    @Mock DeviceApiKeyRepository keyRepo;
    @Mock SensorRepository sensorRepo;
    @Mock I18nService i18n;

    @InjectMocks DeviceKeyService service;

    @Test
    void generate_persistsBcryptHashAndReturnsPlainSecret() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID sid = UUID.randomUUID();
        when(sensorRepo.findById(sid)).thenReturn(Optional.of(new Sensor(
                sid, null, VariableKind.temperature, "C", UUID.randomUUID(),
                300, SensorStatus.active, UUID.randomUUID(), Instant.now(),
                null, null, null)));
        UUID keyId = UUID.randomUUID();
        when(keyRepo.insert(any(), any(), any())).thenReturn(keyId);

        DeviceKeyService.Generated g = service.generate(sid);

        assertThat(g.id()).isEqualTo(keyId);
        assertThat(g.secret()).isNotBlank();

        ArgumentCaptor<String> hashCap = ArgumentCaptor.forClass(String.class);
        verify(keyRepo).insert(any(), hashCap.capture(), any());
        // Round-trip the secret through BCrypt — must match its own hash.
        assertThat(BCrypt.checkpw(g.secret(), hashCap.getValue())).isTrue();
        verify(keyRepo).deactivateAllForSensor(sid);
    }

    @Test
    void generate_sensorMissing_throws404() {
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID sid = UUID.randomUUID();
        when(sensorRepo.findById(sid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(sid))
                .isInstanceOf(SensorNotFoundException.class);
    }
}
