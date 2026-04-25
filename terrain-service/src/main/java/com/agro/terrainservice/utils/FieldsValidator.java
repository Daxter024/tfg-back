package com.agro.terrainservice.utils;

import com.agro.terrainservice.constants.TerrainFields;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FieldsValidator {

    /**
     * Construye la cláusula SELECT a partir de un listado de campos.
     *
     * El campo virtual {@code parcels_summary} se excluye de aquí porque no es
     * una columna real — se calcula con un agregado aparte sobre la tabla
     * `parcel`. Si el cliente lo pide en `fields`, se ignora a nivel SQL y el
     * service hace la composición posterior.
     */
    public String formatFieldList(List<TerrainFields> fields) {

        final List<TerrainFields> fieldsToProcess;

        if (fields == null || fields.isEmpty()) {
            fieldsToProcess = Arrays.stream(TerrainFields.values())
                    .filter(f -> f != TerrainFields.parcels_summary)
                    .toList();
        } else {
            fieldsToProcess = fields.stream()
                    .filter(f -> f != TerrainFields.parcels_summary)
                    .toList();
        }

        if (fieldsToProcess.isEmpty()) {
            // El cliente solo pidió parcels_summary — necesitamos el id como mínimo
            return "id";
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

    public boolean includesParcelsSummary(List<TerrainFields> fields) {
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        return fields.contains(TerrainFields.parcels_summary);
    }
}
