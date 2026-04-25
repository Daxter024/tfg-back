package com.agro.terrainservice.dto;

import java.util.Map;

public record CadastralImportResponse(
        String reference,
        String suggested_name,
        Map<String, Object> geometry,
        Double area_m2,
        String soil_use,
        String soil_class,
        String municipality,
        String province
) {
}
