package com.agro.seasonservice.utils;

import com.agro.seasonservice.constants.SeasonField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FieldsValidator {
    public String formatFieldList(List<SeasonField> fields) {
        return (fields == null || fields.isEmpty())
                ? "*"
                : fields.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
