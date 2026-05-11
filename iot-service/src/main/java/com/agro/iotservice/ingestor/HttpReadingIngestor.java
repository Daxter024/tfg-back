package com.agro.iotservice.ingestor;

import com.agro.iotservice.dto.Reading;
import com.agro.iotservice.service.SensorReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Single v1 implementation of {@link ReadingIngestor}. Pure delegation — keeps
 * the transport layer thin so the domain stays unaware of HTTP vs MQTT vs
 * anything else.
 */
@Service
@RequiredArgsConstructor
public class HttpReadingIngestor implements ReadingIngestor {

    private final SensorReadingService readingService;

    @Override
    public int ingest(UUID sensorId, List<Reading> readings) {
        return readingService.persistAndEvaluate(sensorId, readings);
    }
}
