package com.agro.terrainservice.utils;

import com.agro.terrainservice.constants.TerrainFields;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FieldsValidator {
    public String formatFieldList(List<TerrainFields> fields) {

        final List<TerrainFields> fieldsToProcess;

        if (fields == null || fields.isEmpty()) {
            fieldsToProcess = List.of(TerrainFields.values());
        } else {
            fieldsToProcess = fields;
        }

        return fieldsToProcess.stream()
                .map(field -> {
                    String fieldName = field.name().toLowerCase();
                    switch (fieldName) {
                        case "geometry":
                            return "ST_AsGeoJSON(geometry) AS geometry";
                        case "centroid":
                            return "ST_AsGeoJSON(ST_Centroid(geometry)) AS centroid";
                        default:
                            return fieldName;
                    }
                }).collect(Collectors.joining(","));
    }
}
