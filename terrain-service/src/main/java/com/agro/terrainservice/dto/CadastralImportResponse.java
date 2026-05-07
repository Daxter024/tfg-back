package com.agro.terrainservice.dto;

import java.util.Map;

/**
 * HU-TER-05: propuesta de terreno editable construida a partir de una
 * referencia catastral o SIGPAC. NO se persiste hasta que el cliente
 * confirme la creacion via {@code POST /terrain} con el body completo y
 * {@code cadastral_ref} establecido.
 */
public record CadastralImportResponse(
        String reference,
        String suggested_name,
        Map<String, Object> geometry,   // GeoJSON
        Double area_m2,
        String soil_use,
        String soil_class,
        String municipality,
        String province
) {
}
