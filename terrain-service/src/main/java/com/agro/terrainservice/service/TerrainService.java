package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.event.TerrainDeletedEvent;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldsValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainService {
    private final TerrainRepository terrainRepository;
    private final I18nService i18nService;
    private final ObjectMapper mapper;
    private final FieldsValidator fieldsValidator;
    private final UserGrpcClient userGrpcClient;
    private final EventPublisher eventPublisher;
    private final ParcelService parcelService;

    @Transactional(readOnly = true)
    public Map<String, Object> getTerrain(UUID id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        Map<String, Object> base = terrainRepository.getTerrain(id, selectedFields);
        if (fieldsValidator.includesParcelsSummary(fields)) {
            // El campo virtual parcels_summary se compone aparte
            double areaM2 = base.get("area_m2") instanceof Number n ? n.doubleValue() : 0d;
            base = new java.util.LinkedHashMap<>(base);
            base.put("parcels_summary", parcelService.computeSummary(id, areaM2));
        }
        return base;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTerrains(UUID user_id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return terrainRepository.getTerrains(user_id, selectedFields);
    }

    @Transactional
    public String create(TerrainRequest dto) {
        if (!userGrpcClient.validateUser(dto.user_id())) {
            log.warn("Attempt to create terrain for non-existent user {}", dto.user_id());
            throw new RuntimeException(i18nService.getMessage("user.notfound", dto.user_id()));
        }

        // Validación geométrica temprana — JTS para detectar polígonos
        // inválidos (anillo no cerrado, auto-intersección, < 4 vértices)
        // antes de tocar la BBDD y obtener un mensaje i18n claro.
        validateGeometry(dto.geometry());

        try {
            String geoJson = mapper.writeValueAsString(dto.geometry());
            terrainRepository.saveWithCalculations(
                    dto.name(),
                    dto.user_id(),
                    geoJson,
                    dto.soil_type(),
                    dto.slope_percent(),
                    dto.irrigation(),
                    dto.cadastral_ref()
            );
            return i18nService.getMessage("terrain.created", dto.name());
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            // La constraint terrain_area_range puede dispararse si el área generada
            // cae fuera de rango. Devolver mensaje i18n claro al cliente.
            String reason = dive.getMostSpecificCause() == null
                    ? dive.getMessage() : dive.getMostSpecificCause().getMessage();
            if (reason != null && reason.contains("terrain_area_range")) {
                throw new AreaOutOfRangeException(
                        i18nService.getMessage("terrain.area.out.of.range")
                );
            }
            if (reason != null && (reason.contains("terrain_geom_valid")
                    || reason.contains("ST_IsValid")
                    || reason.contains("terrain_geom_srid"))) {
                throw new InvalidGeometryException(
                        i18nService.getMessage("terrain.geometry.invalid")
                );
            }
            throw dive;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(i18nService.getMessage("error.geojson"), e);
        }
    }

    @Transactional
    public void deleteTerrain(UUID id, UUID userId) {
        // Antes del cascade SQL, emitimos un parcel-deleted por cada parcela
        // del terreno para que los listeners downstream limpien sus datos.
        parcelService.publishCascadeForTerrainDeletion(id);
        terrainRepository.deleteTerrain(id, userId);
        eventPublisher.publishTerrainDeleted(new TerrainDeletedEvent(id));
    }

    @Transactional
    public void deleteTerrainsByUserId(UUID userId) {
        List<UUID> terrainIds = terrainRepository.findIdsByUserId(userId);
        for (UUID id : terrainIds) {
            parcelService.publishCascadeForTerrainDeletion(id);
            terrainRepository.deleteById(id);
            eventPublisher.publishTerrainDeleted(new TerrainDeletedEvent(id));
        }
    }

    /**
     * Valida estructuralmente la geometría GeoJSON: tipo Polygon, anillo
     * exterior cerrado, mínimo 4 vértices (3 únicos + cierre).
     * <p>
     * Las validaciones topológicas profundas (auto-intersección, SRID) las
     * delega en PostGIS — el constraint {@code terrain_geom_valid} las captura
     * y se traducen en {@link InvalidGeometryException} desde el catch
     * {@code DataIntegrityViolationException} de {@link #create(TerrainRequest)}.
     */
    @SuppressWarnings("unchecked")
    private void validateGeometry(Map<String, Object> geometry) {
        Object type = geometry.get("type");
        if (!"Polygon".equals(type)) {
            throw new InvalidGeometryException(
                    i18nService.getMessage("terrain.geometry.invalid")
            );
        }
        Object coordsObj = geometry.get("coordinates");
        if (!(coordsObj instanceof List<?> rings) || rings.isEmpty()) {
            throw new InvalidGeometryException(
                    i18nService.getMessage("terrain.geometry.invalid")
            );
        }
        Object outerRingObj = rings.get(0);
        if (!(outerRingObj instanceof List<?> outer) || outer.size() < 4) {
            throw new InvalidGeometryException(
                    i18nService.getMessage("terrain.geometry.invalid")
            );
        }
        // Ring debe estar cerrado: primer == último vértice
        Object first = outer.get(0);
        Object last = outer.get(outer.size() - 1);
        if (first == null || !first.equals(last)) {
            throw new InvalidGeometryException(
                    i18nService.getMessage("terrain.geometry.invalid")
            );
        }
    }
}
