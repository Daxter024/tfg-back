package com.agro.inputservice.utils;

import com.agro.inputservice.constants.InputField;
import com.agro.inputservice.exception.InvalidFieldException;
import com.agro.inputservice.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Convierte la lista de {@link InputField} pedida por el cliente (parametro
 * {@code fields=...}) en una clausula SQL segura. Si la lista esta vacia o nula
 * usa {@code *}. Los nombres invalidos rebotan por el binding del enum y los
 * gestiona el handler global.
 */
@Component
@RequiredArgsConstructor
public class FieldsValidator {

    private final I18nService i18nService;

    public String formatFieldList(List<InputField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "*";
        }
        return fields.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public void validateField(String raw) {
        try {
            InputField.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldException(
                    i18nService.getMessage("input.invalid.argument", raw));
        }
    }
}
