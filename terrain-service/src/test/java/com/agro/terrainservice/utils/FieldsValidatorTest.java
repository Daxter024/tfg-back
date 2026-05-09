package com.agro.terrainservice.utils;

import com.agro.terrainservice.constants.TerrainFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de la whitelist de campos para la proyeccion dinamica.
 * Cubre TER-4.01–4.10 desde el angulo del componente: el controller delega
 * la validacion del enum a Spring (lo cual se testea en {@code TerrainControllerTest}).
 */
class FieldsValidatorTest {

    private final FieldsValidator validator = new FieldsValidator();

    @Test
    @DisplayName("TER-4.01 - sin fields devuelve todos los campos del enum")
    void formatFieldList_returnsAllFields_whenNullOrEmpty() {
        String all = validator.formatFieldList(null);
        for (TerrainFields f : TerrainFields.values()) {
            String expectedToken = switch (f) {
                case geometry -> "ST_AsGeoJSON(geometry) AS geometry";
                case centroid -> "ST_AsGeoJSON(ST_Centroid(geometry)) AS centroid";
                default -> f.name().toLowerCase();
            };
            assertThat(all).contains(expectedToken);
        }
    }

    @Test
    @DisplayName("TER-4.08 - fields vacio se trata como sin fields")
    void formatFieldList_returnsAllFields_whenEmptyList() {
        String all = validator.formatFieldList(List.of());
        assertThat(all).contains("id").contains("name");
    }

    @Test
    @DisplayName("TER-4.02 - una sola entrada produce un solo token")
    void formatFieldList_singleField() {
        String result = validator.formatFieldList(List.of(TerrainFields.name));
        assertThat(result).isEqualTo("name");
    }

    @Test
    @DisplayName("TER-4.03 - varias entradas son separadas por coma")
    void formatFieldList_multipleFields() {
        String result = validator.formatFieldList(List.of(
                TerrainFields.id, TerrainFields.name, TerrainFields.area_m2));
        assertThat(result).isEqualTo("id,name,area_m2");
    }

    @Test
    @DisplayName("TER-4.04 - geometry usa ST_AsGeoJSON")
    void formatFieldList_geometryFieldExpandsToStAsGeoJson() {
        String result = validator.formatFieldList(List.of(TerrainFields.geometry));
        assertThat(result).contains("ST_AsGeoJSON(geometry)");
        assertThat(result).contains("AS geometry");
    }

    @Test
    @DisplayName("TER-4.05 - centroid usa ST_AsGeoJSON(ST_Centroid(...))")
    void formatFieldList_centroidFieldExpandsToCentroidExpression() {
        String result = validator.formatFieldList(List.of(TerrainFields.centroid));
        assertThat(result).contains("ST_AsGeoJSON(ST_Centroid(geometry))");
        assertThat(result).contains("AS centroid");
    }

    @Test
    @DisplayName("TER-4.09 - duplicados producen tokens duplicados (idempotente en SQL)")
    void formatFieldList_duplicates() {
        String result = validator.formatFieldList(List.of(
                TerrainFields.id, TerrainFields.id, TerrainFields.name));
        assertThat(result).isEqualTo("id,id,name");
    }
}
