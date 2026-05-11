package com.agro.iotservice.service;

import com.agro.iotservice.client.TerrainGrpcClient;
import com.agro.iotservice.constants.SensorStatus;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.SensorRequest;
import com.agro.iotservice.dto.SensorUpdateRequest;
import com.agro.iotservice.exception.SensorNotFoundException;
import com.agro.iotservice.exception.TerrainNotFoundException;
import com.agro.iotservice.model.Sensor;
import com.agro.iotservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for the {@code sensor} aggregate. Terrain existence is verified
 * via gRPC at creation time (no FK across services). Defaults
 * {@code expected_interval_seconds} to 300 s when the caller omits it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorService {

    private static final int DEFAULT_INTERVAL_SECONDS = 300;

    private final SensorRepository repository;
    private final TerrainGrpcClient terrainClient;
    private final I18nService i18n;

    @Transactional(readOnly = true)
    public Sensor getById(UUID id) {
        Sensor base = repository.findById(id)
                .orElseThrow(() -> new SensorNotFoundException(i18n.getMessage("sensor.not.found")));
        // Populate last_value lazily for detail view.
        var lastValue = repository.findLastValue(id).orElse(null);
        return new Sensor(
                base.id(), base.external_id(), base.variable(), base.unit(),
                base.terrain_id(), base.expected_interval_seconds(), base.status(),
                base.created_by(), base.created_at(), base.last_reading_at(),
                lastValue, null);
    }

    @Transactional(readOnly = true)
    public List<Sensor> search(UUID terrainId, VariableKind variable, SensorStatus status) {
        return repository.search(terrainId, variable, status);
    }

    @Transactional
    public UUID create(SensorRequest req, UUID createdBy) {
        if (!terrainClient.checkTerrainExists(req.terrain_id())) {
            throw new TerrainNotFoundException(i18n.getMessage("sensor.terrain.not.found"));
        }
        int interval = req.expected_interval_seconds() != null
                ? req.expected_interval_seconds()
                : DEFAULT_INTERVAL_SECONDS;
        return repository.insert(
                req.external_id(),
                req.variable(),
                req.unit(),
                req.terrain_id(),
                interval,
                createdBy);
    }

    @Transactional
    public void update(UUID id, SensorUpdateRequest req) {
        if (repository.findById(id).isEmpty()) {
            throw new SensorNotFoundException(i18n.getMessage("sensor.not.found"));
        }
        int rows = repository.updatePartial(
                id,
                req.unit(),
                req.expected_interval_seconds(),
                req.status(),
                req.external_id());
        if (rows == 0) {
            throw new SensorNotFoundException(i18n.getMessage("sensor.not.found"));
        }
    }

    @Transactional
    public void delete(UUID id) {
        int rows = repository.delete(id);
        if (rows == 0) {
            throw new SensorNotFoundException(i18n.getMessage("sensor.not.found"));
        }
    }

    /**
     * Used by {@code TerrainDeletedListener} (Kafka).
     *
     * @return rows removed.
     */
    @Transactional
    public int deleteByTerrainId(UUID terrainId) {
        return repository.deleteByTerrainId(terrainId);
    }

    /**
     * Used by {@code UserDeletedListener} (Kafka, RGPD policy).
     *
     * @return rows removed.
     */
    @Transactional
    public int deleteByCreatedBy(UUID userId) {
        return repository.deleteByCreatedBy(userId);
    }
}
