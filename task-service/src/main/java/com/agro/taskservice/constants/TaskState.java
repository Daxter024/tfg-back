package com.agro.taskservice.constants;

/**
 * Estados de una tarea agraria. Las transiciones validas se documentan en
 * {@code service.TaskTransitionService}:
 * <pre>
 * PENDING     -&gt; IN_PROGRESS | CANCELLED
 * IN_PROGRESS -&gt; FINISHED   | CANCELLED
 * FINISHED    -&gt; (inmutable)
 * CANCELLED   -&gt; (inmutable)
 * </pre>
 */
public enum TaskState {
    PENDING,
    IN_PROGRESS,
    FINISHED,
    CANCELLED
}
