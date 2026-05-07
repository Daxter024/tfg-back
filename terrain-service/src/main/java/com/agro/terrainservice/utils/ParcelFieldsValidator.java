package com.agro.terrainservice.utils;

import com.agro.terrainservice.constants.ParcelFields;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ParcelFieldsValidator {

    public String formatFieldList(List<ParcelFields> fields) {
        final List<ParcelFields> fieldsToProcess = (fields == null || fields.isEmpty())
                ? List.of(ParcelFields.values())
                : fields;

        return fieldsToProcess.stream()
                .map(field -> {
                    String fieldName = field.name().toLowerCase();
                    return switch (fieldName) {
                        case "geometry" -> "ST_AsGeoJSON(geometry) AS geometry";
                        case "centroid" -> "ST_AsGeoJSON(ST_Centroid(geometry)) AS centroid";
                        default -> fieldName;
                    };
                })
                .collect(Collectors.joining(","));
    }
}
