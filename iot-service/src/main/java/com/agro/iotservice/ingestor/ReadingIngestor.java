package com.agro.iotservice.ingestor;

import com.agro.iotservice.dto.Reading;

import java.util.List;
import java.util.UUID;

/**
 * Transport-agnostic ingestion port. Persists a batch of readings for a
 * sensor. Implementations MUST be idempotent on (sensor_id, recorded_at)
 * duplicates — the underlying persistence uses
 * {@code INSERT ... ON CONFLICT DO NOTHING}.
 *
 * <p>Decision D4 (2026-05-11): only HTTP in v1. When MQTT is added, create
 * MqttReadingIngestor that receives messages from
 * {@code sensors/{sensor_id}/reading} and delegates to the same
 * SensorReadingService.persistAndEvaluate. The domain MUST NOT change.</p>
 */
public interface ReadingIngestor {

    /**
     * @return number of rows actually inserted (duplicates are silently
     * skipped).
     */
    int ingest(UUID sensorId, List<Reading> readings);
}
