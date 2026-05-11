package com.agro.inputservice.constants;

/**
 * Causas validas para registrar un movimiento. {@code TASK} y {@code
 * TASK_REVERT} solo los usa el listener Kafka interno; rechazar si vienen por
 * el endpoint REST.
 */
public final class MovementReason {

    public static final String PURCHASE = "PURCHASE";
    public static final String DONATION = "DONATION";
    public static final String LOSS = "LOSS";
    public static final String EXPIRATION = "EXPIRATION";
    public static final String TASK = "TASK";
    public static final String TASK_REVERT = "TASK_REVERT";
    public static final String OTHER = "OTHER";

    private MovementReason() {}

    public static boolean isInternal(String reason) {
        return TASK.equals(reason) || TASK_REVERT.equals(reason);
    }
}
