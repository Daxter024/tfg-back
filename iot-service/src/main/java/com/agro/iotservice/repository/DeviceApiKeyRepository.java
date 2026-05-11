package com.agro.iotservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@code device_api_key}. Only stores BCrypt digests of the
 * device secret — the plain key is returned ONCE at generation time.
 */
@Repository
@RequiredArgsConstructor
public class DeviceApiKeyRepository {

    private final JdbcTemplate jdbc;

    public UUID insert(UUID sensorId, String keyHash, UUID rotatedFrom) {
        return jdbc.queryForObject("""
                        INSERT INTO device_api_key (sensor_id, key_hash, rotated_from)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                UUID.class, sensorId, keyHash, rotatedFrom);
    }

    public int deactivateAllForSensor(UUID sensorId) {
        return jdbc.update("UPDATE device_api_key SET active = FALSE WHERE sensor_id = ? AND active = TRUE",
                sensorId);
    }

    public List<String> findActiveHashes(UUID sensorId) {
        return jdbc.queryForList(
                "SELECT key_hash FROM device_api_key WHERE sensor_id = ? AND active = TRUE",
                String.class, sensorId);
    }

    /**
     * Verifies that {@code plainKey} matches any currently-active hash for
     * {@code sensorId}.
     */
    public boolean verifyActiveKey(UUID sensorId, String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return false;
        }
        for (String hash : findActiveHashes(sensorId)) {
            try {
                if (BCrypt.checkpw(plainKey, hash)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Malformed hash — skip; do not leak.
            }
        }
        return false;
    }
}
