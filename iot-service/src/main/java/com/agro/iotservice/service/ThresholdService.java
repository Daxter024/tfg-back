package com.agro.iotservice.service;

import com.agro.iotservice.client.UserGrpcClient;
import com.agro.iotservice.constants.VariableKind;
import com.agro.iotservice.dto.ThresholdRequest;
import com.agro.iotservice.dto.ThresholdUpdateRequest;
import com.agro.iotservice.exception.InvalidThresholdException;
import com.agro.iotservice.exception.ThresholdNotFoundException;
import com.agro.iotservice.model.Threshold;
import com.agro.iotservice.repository.ThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Use cases for the {@code threshold} aggregate. Validates the XOR scope
 * (sensor vs variable), at-least-one-bound, ordered bounds and every
 * notify_user_id via gRPC ValidateUser before persisting. The DB CHECK
 * constraints back up the same rules at storage time.
 */
@Service
@RequiredArgsConstructor
public class ThresholdService {

    private final ThresholdRepository repository;
    private final UserGrpcClient userClient;
    private final I18nService i18n;

    @Transactional(readOnly = true)
    public List<Threshold> search(UUID sensorId, VariableKind variable) {
        return repository.search(sensorId, variable);
    }

    @Transactional(readOnly = true)
    public Threshold getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ThresholdNotFoundException(i18n.getMessage("threshold.not.found")));
    }

    @Transactional
    public UUID create(ThresholdRequest req) {
        validateScope(req.sensor_id(), req.variable());
        validateBounds(req.min_value(), req.max_value());
        validateNotify(req.notify_user_ids());
        return repository.insert(
                req.sensor_id(),
                req.variable(),
                req.min_value(),
                req.max_value(),
                req.notify_user_ids());
    }

    @Transactional
    public void update(UUID id, ThresholdUpdateRequest req) {
        Threshold existing = repository.findById(id)
                .orElseThrow(() -> new ThresholdNotFoundException(i18n.getMessage("threshold.not.found")));

        BigDecimal newMin = req.min_value() != null ? req.min_value()
                : (Boolean.TRUE.equals(req.clear_min()) ? null : existing.min_value());
        BigDecimal newMax = req.max_value() != null ? req.max_value()
                : (Boolean.TRUE.equals(req.clear_max()) ? null : existing.max_value());
        validateBounds(newMin, newMax);
        if (req.notify_user_ids() != null) {
            validateNotify(req.notify_user_ids());
        }
        int rows = repository.updatePartial(
                id,
                req.min_value(),
                req.max_value(),
                req.notify_user_ids(),
                Boolean.TRUE.equals(req.clear_min()),
                Boolean.TRUE.equals(req.clear_max()));
        if (rows == 0) {
            throw new ThresholdNotFoundException(i18n.getMessage("threshold.not.found"));
        }
    }

    @Transactional
    public void delete(UUID id) {
        int rows = repository.delete(id);
        if (rows == 0) {
            throw new ThresholdNotFoundException(i18n.getMessage("threshold.not.found"));
        }
    }

    private void validateScope(UUID sensorId, VariableKind variable) {
        boolean hasSensor = sensorId != null;
        boolean hasVariable = variable != null;
        if (hasSensor == hasVariable) {
            throw new InvalidThresholdException(i18n.getMessage("threshold.target.xor"));
        }
    }

    private void validateBounds(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            throw new InvalidThresholdException(i18n.getMessage("threshold.bounds.meaningful"));
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new InvalidThresholdException(i18n.getMessage("threshold.bounds.ordered"));
        }
    }

    private void validateNotify(List<UUID> notify) {
        if (notify == null || notify.isEmpty()) return;
        for (UUID u : notify) {
            if (u == null || !userClient.validateUser(u)) {
                throw new InvalidThresholdException(i18n.getMessage("threshold.notify.user.not.found"));
            }
        }
    }
}
