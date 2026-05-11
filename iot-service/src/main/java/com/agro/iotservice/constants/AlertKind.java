package com.agro.iotservice.constants;

/**
 * Kind of threshold breach for a {@code sensor_alert}. Matches the
 * {@code alert_kind} Postgres enum in V2.
 */
public enum AlertKind {
    below_min,
    above_max
}
