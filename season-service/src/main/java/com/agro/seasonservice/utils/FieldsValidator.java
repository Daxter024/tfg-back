package com.agro.seasonservice.utils;

import com.agro.seasonservice.exception.InvalidFieldException;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: Build a Library in order to avoid duplicity

@RequiredArgsConstructor
public class FieldsValidator {

    private final Set<String> allowedFields;

    public String validateAndProcess(String fields) {
        if (fields == null || fields.trim().isEmpty()) {
            return "*";
        }

        Set<String> requestedFields = parseFields(fields);
        Set<String> invalidFields = findInvalidFields(requestedFields);

        if (!invalidFields.isEmpty()) {
            throw new InvalidFieldException(
                    "Campos solicitados inválidos " + String.join(", ", invalidFields) +
                            ". Campos permitidos: " + String.join(", ", allowedFields)
            );
        }

        return String.join(", ", requestedFields);
    }

    private Set<String> parseFields(String fields) {
        return Arrays.stream(fields.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private Set<String> findInvalidFields(Set<String> requestedFields) {
        return requestedFields.stream()
                .filter(f -> !allowedFields.contains(f))
                .collect(Collectors.toSet());
    }
}
