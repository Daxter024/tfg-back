package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ParcelFields;
import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.event.ParcelDeletedEvent;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.ParcelNotFoundException;
import com.agro.terrainservice.exception.ParcelNotWithinTerrainException;
import com.agro.terrainservice.exception.ParcelOverlapsException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.repository.ParcelRepository;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.ParcelFieldsValidator;
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
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelFieldsValidator fieldsValidator;
    private final I18nService i18nService;
    private final ObjectMapper mapper;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getParcels(UUID terrainId, List<ParcelFields> fields) {
        ensureTerrainExists(terrainId);
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return parcelRepository.getParcels(terrainId, selectedFields);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getParcel(UUID terrainId, UUID parcelId, List<ParcelFields> fields) {
        ensureTerrainExists(terrainId);
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return parcelRepository.getParcel(terrainId, parcelId, selectedFields);
    }

    @Transactional
    public UUID create(UUID terrainId, ParcelRequest dto) {
        ensureTerrainExists(terrainId);
        validateGeometryStructure(dto.geometry());

        String geoJson;
        try {
            geoJson = mapper.writeValueAsString(dto.geometry());
        } catch (Exception e) {
            throw new RuntimeException(i18nService.getMessage("error.geojson"), e);
        }

        if (!parcelRepository.isWithinTerrain(terrainId, geoJson)) {
            throw new ParcelNotWithinTerrainException(
                    i18nService.getMessage("parcel.not.within.terrain")
            );
        }

        String overlapping = parcelRepository.findOverlappingParcelName(terrainId, geoJson, null);
        if (overlapping != null) {
            throw new ParcelOverlapsException(
                    i18nService.getMessage("parcel.overlaps", overlapping)
            );
        }

        try {
            return parcelRepository.save(terrainId, dto.name(), geoJson);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            String reason = dive.getMostSpecificCause() == null
                    ? dive.getMessage() : dive.getMostSpecificCause().getMessage();
            if (reason != null && (reason.contains("parcel_geom_valid")
                    || reason.contains("ST_IsValid"))) {
                throw new InvalidGeometryException(
                        i18nService.getMessage("terrain.geometry.invalid")
                );
            }
            throw dive;
        }
    }

    @Transactional
    public void update(UUID terrainId, UUID parcelId, ParcelUpdateRequest dto) {
        ensureTerrainExists(terrainId);
        if (dto.name() == null && dto.geometry() == null) {
            throw new IllegalArgumentException(
                    i18nService.getMessage("parcel.update.empty")
            );
        }
        // Confirma que la parcela existe y es de este terreno (lanza 404 si no).
        parcelRepository.getParcel(terrainId, parcelId, "id");

        if (dto.name() != null) {
            parcelRepository.updateName(parcelId, terrainId, dto.name());
        }
        if (dto.geometry() != null) {
            validateGeometryStructure(dto.geometry());
            String geoJson;
            try {
                geoJson = mapper.writeValueAsString(dto.geometry());
            } catch (Exception e) {
                throw new RuntimeException(i18nService.getMessage("error.geojson"), e);
            }
            if (!parcelRepository.isWithinTerrain(terrainId, geoJson)) {
                throw new ParcelNotWithinTerrainException(
                        i18nService.getMessage("parcel.not.within.terrain")
                );
            }
            String overlapping = parcelRepository.findOverlappingParcelName(terrainId, geoJson, parcelId);
            if (overlapping != null) {
                throw new ParcelOverlapsException(
                        i18nService.getMessage("parcel.overlaps", overlapping)
                );
            }
            parcelRepository.updateGeometry(parcelId, terrainId, geoJson);
        }
    }

    @Transactional
    public void delete(UUID terrainId, UUID parcelId) {
        ensureTerrainExists(terrainId);
        int rows = parcelRepository.deleteById(parcelId, terrainId);
        if (rows == 0) {
            throw new ParcelNotFoundException(
                    i18nService.getMessage("parcel.notfound", parcelId)
            );
        }
        eventPublisher.publishParcelDeleted(new ParcelDeletedEvent(parcelId, terrainId));
    }

    /**
     * Antes del DELETE de un terreno, listamos las parcelas y emitimos un
     * evento {@code parcel-deleted} por cada una. El cascade SQL las borrará
     * efectivamente; los listeners downstream tendrán el evento.
     */
    public void publishCascadeForTerrainDeletion(UUID terrainId) {
        List<UUID> parcelIds = parcelRepository.findIdsByTerrainId(terrainId);
        for (UUID parcelId : parcelIds) {
            eventPublisher.publishParcelDeleted(new ParcelDeletedEvent(parcelId, terrainId));
        }
    }

    /**
     * Calcula el resumen agregado para la ficha de terreno (HU-TER-04 §3.6):
     * número de parcelas, área parcelada y área no parcelada.
     */
    public Map<String, Object> computeSummary(UUID terrainId, double terrainAreaM2) {
        int count = parcelRepository.countByTerrainId(terrainId);
        double parceledArea = parcelRepository.sumAreaByTerrainId(terrainId);
        double unparceled = Math.max(0d, terrainAreaM2 - parceledArea);
        return Map.of(
                "parcel_count", count,
                "area_parceled_m2", parceledArea,
                "area_unparceled_m2", unparceled
        );
    }

    private void ensureTerrainExists(UUID terrainId) {
        if (!terrainRepository.existsById(terrainId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }
    }

    /** Validación estructural mínima del polígono. Coherente con TerrainService. */
    @SuppressWarnings("unchecked")
    private void validateGeometryStructure(Map<String, Object> geometry) {
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
        Object first = outer.get(0);
        Object last = outer.get(outer.size() - 1);
        if (first == null || !first.equals(last)) {
            throw new InvalidGeometryException(
                    i18nService.getMessage("terrain.geometry.invalid")
            );
        }
    }
}
