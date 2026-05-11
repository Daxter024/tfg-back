package com.agro.taskservice.utils;

import com.agro.taskservice.constants.TaskField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Traduce una lista de {@link TaskField} a la clausula {@code SELECT} SQL.
 * Si la lista es vacia o nula → {@code *}. Solo los nombres del enum
 * pueden interpolarse (proteccion SQLi).
 */
@Component
public class FieldsValidator {

    public String formatFieldList(List<TaskField> fields) {
        return (fields == null || fields.isEmpty())
                ? "*"
                : fields.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
