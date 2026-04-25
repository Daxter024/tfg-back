package com.agro.terrainservice.utils;

import com.agro.terrainservice.constants.ParcelFields;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ParcelFieldsValidator {

    public String formatFieldList(List<ParcelFields> fields) {
        final List<ParcelFields> fieldsToProcess;
        if (fields == null || fields.isEmpty()) {
            fieldsToProcess = Arrays.asList(ParcelFields.values());
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
                })
                .collect(Collectors.joining(","));
    }
}
