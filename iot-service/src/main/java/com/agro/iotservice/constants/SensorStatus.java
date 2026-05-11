package com.agro.iotservice.constants;

/**
 * Sensor lifecycle status. Matches the CHECK constraint on sensor.status in
 * the V1 migration.
 */
public enum SensorStatus {
    active,
    inactive,
    no_signal
}
