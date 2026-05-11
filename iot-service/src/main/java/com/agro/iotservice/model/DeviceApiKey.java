package com.agro.iotservice.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A device API key for a sensor. {@code key_hash} stores a BCrypt digest of
 * the secret. The plain secret is shown ONCE at generation time and is never
 * persisted.
 */
public record DeviceApiKey(
        UUID id,
        UUID sensor_id,
        String key_hash,
        boolean active,
        Instant created_at,
        UUID rotated_from
) {
}
