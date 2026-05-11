package com.agro.iotservice.constants;

/**
 * Lifecycle of a sensor alert. Matches the {@code alert_state} Postgres enum
 * in V2.
 */
public enum AlertState {
    new_,
    reviewed,
    resolved;

    /**
     * Postgres-side spelling for the enum {@code new}. Java reserves the
     * keyword so we map {@code new_} -> {@code "new"} for SQL.
     */
    public String dbValue() {
        return this == new_ ? "new" : name();
    }

    public static AlertState fromDb(String v) {
        return switch (v) {
            case "new" -> new_;
            case "reviewed" -> reviewed;
            case "resolved" -> resolved;
            default -> throw new IllegalArgumentException("Unknown alert_state: " + v);
        };
    }
}
