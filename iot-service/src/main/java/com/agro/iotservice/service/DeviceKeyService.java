package com.agro.iotservice.service;

import com.agro.iotservice.exception.SensorNotFoundException;
import com.agro.iotservice.repository.DeviceApiKeyRepository;
import com.agro.iotservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates per-sensor API keys. The plain secret is returned ONCE to the
 * caller (admin endpoint POST /sensor/{id}/api-key) and never persisted —
 * only a BCrypt digest is stored. Any previously active key for the sensor
 * is deactivated atomically as part of the rotation.
 */
@Service
@RequiredArgsConstructor
public class DeviceKeyService {

    private static final SecureRandom RNG = new SecureRandom();
    /** 32 random bytes -> 43-char base64url string. Reasonable industry size. */
    private static final int SECRET_BYTES = 32;
    /** BCrypt cost 12 ~ 250 ms on modern hardware. Acceptable for one-shot
     *  generation; verification on /ingest uses the same hash. */
    private static final int BCRYPT_COST = 12;

    private final DeviceApiKeyRepository keyRepo;
    private final SensorRepository sensorRepo;
    private final I18nService i18n;

    @Transactional
    public Generated generate(UUID sensorId) {
        if (sensorRepo.findById(sensorId).isEmpty()) {
            throw new SensorNotFoundException(i18n.getMessage("sensor.not.found"));
        }
        keyRepo.deactivateAllForSensor(sensorId);
        String secret = newSecret();
        String hash = BCrypt.hashpw(secret, BCrypt.gensalt(BCRYPT_COST));
        UUID keyId = keyRepo.insert(sensorId, hash, null);
        return new Generated(keyId, secret);
    }

    private String newSecret() {
        byte[] buf = new byte[SECRET_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record Generated(UUID id, String secret) {
    }
}
