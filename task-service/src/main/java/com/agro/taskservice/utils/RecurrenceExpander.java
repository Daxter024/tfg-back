package com.agro.taskservice.utils;

import com.agro.taskservice.dto.RecurrenceSpec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Expansor de reglas de recurrencia simplificadas (FREQ + INTERVAL + UNTIL).
 * Soporta {@code DAILY}, {@code WEEKLY} y {@code MONTHLY}.
 *
 * <p>Limite duro: {@value #MAX_INSTANCES}. Si la expansion supera ese numero,
 * se devuelve una lista incompleta y el llamador debe rechazar la peticion
 * (ver {@code TaskService}).</p>
 */
@Component
public class RecurrenceExpander {

    /** Limite duro de instancias hijas — protege contra peticiones abusivas. */
    public static final int MAX_INSTANCES = 365;

    /**
     * Calcula las fechas de las instancias hijas a partir de la plantilla.
     *
     * @param plannedAt fecha/hora del primer evento (la "plantilla").
     * @param spec      regla de recurrencia.
     * @return lista de {@code LocalDateTime} <strong>sin</strong> incluir la
     *         plantilla — solo los hijos {@code &gt;= plannedAt + 1 paso}.
     *         Puede tener tamano {@code &gt; MAX_INSTANCES}; el llamador debe
     *         comprobarlo y abortar si procede.
     */
    public List<LocalDateTime> expand(LocalDateTime plannedAt, RecurrenceSpec spec) {
        if (spec == null) {
            return List.of();
        }
        int interval = (spec.interval() == null || spec.interval() < 1) ? 1 : spec.interval();
        LocalDate untilDate = spec.until();
        LocalTime timeOfDay = plannedAt.toLocalTime();
        List<LocalDateTime> out = new ArrayList<>();

        LocalDateTime cursor = nextOccurrence(plannedAt, spec.frequency(), interval);
        while (!cursor.toLocalDate().isAfter(untilDate)) {
            out.add(LocalDateTime.of(cursor.toLocalDate(), timeOfDay));
            if (out.size() > MAX_INSTANCES) {
                return out;
            }
            cursor = nextOccurrence(cursor, spec.frequency(), interval);
        }
        return out;
    }

    private LocalDateTime nextOccurrence(LocalDateTime current,
                                         RecurrenceSpec.Frequency frequency,
                                         int interval) {
        return switch (frequency) {
            case DAILY -> current.plusDays(interval);
            case WEEKLY -> current.plusWeeks(interval);
            case MONTHLY -> current.plusMonths(interval);
        };
    }
}
