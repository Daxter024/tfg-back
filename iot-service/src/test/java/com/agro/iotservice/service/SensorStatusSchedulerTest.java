package com.agro.iotservice.service;

import com.agro.iotservice.repository.SensorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorStatusSchedulerTest {

    @Mock SensorRepository repository;

    @InjectMocks SensorStatusScheduler scheduler;

    @Test
    void tick_callsMarkNoSignalIfStale() {
        when(repository.markNoSignalIfStale()).thenReturn(3);
        scheduler.markStaleSensorsNoSignal();
        verify(repository).markNoSignalIfStale();
    }
}
