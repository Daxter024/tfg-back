package com.agro.iotservice.service;

import com.agro.iotservice.constants.AlertKind;
import com.agro.iotservice.constants.AlertState;
import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.event.SensorAlertEvent;
import com.agro.iotservice.kafka.EventPublisher;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.model.SensorAlert;
import com.agro.iotservice.model.Threshold;
import com.agro.iotservice.repository.SensorAlertRepository;
import com.agro.iotservice.repository.SensorRepository;
import com.agro.iotservice.repository.ThresholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceTest {

    @Mock SensorReadingService readingService;
    @Mock SensorRepository sensorRepo;
    @Mock ThresholdRepository thresholdRepo;
    @Mock SensorAlertRepository alertRepo;
    @Mock EventPublisher publisher;
    @Mock I18nService i18n;

    @InjectMocks AlertService service;

    private UUID sensorId;
    private UUID terrainId;
    private UUID thresholdId;
    private Sensor sensor;

    @BeforeEach
    void setUp() {
        sensorId = UUID.randomUUID();
        terrainId = UUID.randomUUID();
        thresholdId = UUID.randomUUID();
        sensor = new Sensor(sensorId, null, VariableKind.temperature, "C",
                terrainId, 300, SensorStatus.active, UUID.randomUUID(),
                Instant.now(), null, null, null);
        when(sensorRepo.findById(sensorId)).thenReturn(Optional.of(sensor));
        when(i18n.getMessage(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void inRangeReading_noAlertOrEvent_butAutoResolvesOpen() {
        Threshold t = new Threshold(thresholdId, sensorId, null,
                new BigDecimal("10"), new BigDecimal("30"),
                List.of(UUID.randomUUID()), Instant.now());
        when(thresholdRepo.resolveForSensor(sensorId, VariableKind.temperature))
                .thenReturn(Optional.of(t));
        when(alertRepo.autoResolveOpenForSensor(sensorId)).thenReturn(1);

        service.evaluate(sensorId, new BigDecimal("21"), Instant.now());

        verify(alertRepo).autoResolveOpenForSensor(sensorId);
        verify(publisher, never()).publishSensorAlert(any());
        verify(alertRepo, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void firstOutOfRangeReading_insertsAlertAndPublishesEvent() {
        UUID notify = UUID.randomUUID();
        Threshold t = new Threshold(thresholdId, sensorId, null,
                new BigDecimal("10"), new BigDecimal("30"),
                List.of(notify), Instant.now());
        when(thresholdRepo.resolveForSensor(sensorId, VariableKind.temperature))
                .thenReturn(Optional.of(t));
        when(alertRepo.findOpenByKind(sensorId, AlertKind.above_max))
                .thenReturn(Optional.empty());
        UUID alertId = UUID.randomUUID();
        when(alertRepo.insert(any(), any(), any(), any(), any())).thenReturn(alertId);

        Instant recordedAt = Instant.parse("2026-05-11T12:00:00Z");
        service.evaluate(sensorId, new BigDecimal("35"), recordedAt);

        verify(alertRepo).insert(sensorId, thresholdId, AlertKind.above_max,
                new BigDecimal("35"), recordedAt);

        ArgumentCaptor<SensorAlertEvent> cap = ArgumentCaptor.forClass(SensorAlertEvent.class);
        verify(publisher).publishSensorAlert(cap.capture());
        SensorAlertEvent ev = cap.getValue();
        assertThat(ev.alertId()).isEqualTo(alertId);
        assertThat(ev.terrainId()).isEqualTo(terrainId);
        assertThat(ev.kind()).isEqualTo("above_max");
        assertThat(ev.threshold()).isEqualByComparingTo("30");
        assertThat(ev.notifyUserIds()).containsExactly(notify);
    }

    @Test
    void subsequentOutOfRangeReading_incrementsCountWithoutPublishing() {
        Threshold t = new Threshold(thresholdId, sensorId, null,
                new BigDecimal("10"), new BigDecimal("30"),
                List.of(), Instant.now());
        when(thresholdRepo.resolveForSensor(sensorId, VariableKind.temperature))
                .thenReturn(Optional.of(t));
        SensorAlert open = new SensorAlert(UUID.randomUUID(), sensorId, thresholdId,
                AlertKind.above_max, new BigDecimal("35"),
                Instant.now(), Instant.now(), 1, AlertState.new_,
                null, null, null, null);
        when(alertRepo.findOpenByKind(sensorId, AlertKind.above_max))
                .thenReturn(Optional.of(open));

        service.evaluate(sensorId, new BigDecimal("33"), Instant.now());

        verify(alertRepo).incrementCount(org.mockito.ArgumentMatchers.eq(open.id()),
                any(Instant.class));
        verify(alertRepo, never()).insert(any(), any(), any(), any(), any());
        verify(publisher, never()).publishSensorAlert(any());
    }

    @Test
    void belowMin_publishesWithMinThresholdInPayload() {
        Threshold t = new Threshold(thresholdId, sensorId, null,
                new BigDecimal("10"), new BigDecimal("30"),
                List.of(), Instant.now());
        when(thresholdRepo.resolveForSensor(sensorId, VariableKind.temperature))
                .thenReturn(Optional.of(t));
        when(alertRepo.findOpenByKind(sensorId, AlertKind.below_min))
                .thenReturn(Optional.empty());
        when(alertRepo.insert(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        service.evaluate(sensorId, new BigDecimal("5"), Instant.now());

        ArgumentCaptor<SensorAlertEvent> cap = ArgumentCaptor.forClass(SensorAlertEvent.class);
        verify(publisher).publishSensorAlert(cap.capture());
        assertThat(cap.getValue().kind()).isEqualTo("below_min");
        assertThat(cap.getValue().threshold()).isEqualByComparingTo("10");
    }

    @Test
    void noThreshold_doesNothing() {
        when(thresholdRepo.resolveForSensor(sensorId, VariableKind.temperature))
                .thenReturn(Optional.empty());

        service.evaluate(sensorId, new BigDecimal("999"), Instant.now());

        verify(alertRepo, never()).insert(any(), any(), any(), any(), any());
        verify(publisher, never()).publishSensorAlert(any());
    }

    @Test
    void sensorMissing_skipsEvaluation() {
        UUID unknown = UUID.randomUUID();
        when(sensorRepo.findById(unknown)).thenReturn(Optional.empty());

        service.evaluate(unknown, new BigDecimal("0"), Instant.now());

        verify(thresholdRepo, never()).resolveForSensor(any(), any());
    }
}
