package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ParcelFields;
import com.agro.terrainservice.dto.ParcelRequest;
import com.agro.terrainservice.dto.ParcelUpdateRequest;
import com.agro.terrainservice.event.ParcelDeletedEvent;
import com.agro.terrainservice.exception.DuplicateParcelNameException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.ParcelNotFoundException;
import com.agro.terrainservice.exception.ParcelNotWithinTerrainException;
import com.agro.terrainservice.exception.ParcelOverlapException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.repository.ParcelRepository;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.ParcelFieldsValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de parcelas (HU-TER-04). Aplica las validaciones geometricas
 * (contencion en terreno padre, no solapamiento) y mantiene la unicidad de
 * nombres por terreno. Publica {@code parcel-deleted} en cada borrado.
 */
@Service
@RequiredArgsConstructor
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelFieldsValidator parcelFieldsValidator;
    private final ObjectMapper mapper;
    private final I18nService i18nService;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID terrainId, List<ParcelFields> fields) {
        ensureTerrainExists(terrainId);
        String selectedFields = parcelFieldsValidator.formatFieldList(fields);
        return parcelRepository.getParcelsByTerrain(terrainId, selectedFields);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID terrainId, UUID parcelId, List<ParcelFields> fields) {
        String selectedFields = parcelFieldsValidator.formatFieldList(fields);
        return parcelRepository.getParcel(terrainId, parcelId, selectedFields);
    }

    @Transactional
    public UUID create(UUID terrainId, ParcelRequest dto) {
        ensureTerrainExists(terrainId);

        String geoJson = serializeGeometry(dto.geometry());

        if (parcelRepository.nameExistsInTerrain(terrainId, dto.name(), null)) {
            throw new DuplicateParcelNameException(
                    i18nService.getMessage("parcel.name.duplicate", dto.name())
            );
        }

        validateContainmentAndOverlap(terrainId, geoJson, null);

        try {
            return parcelRepository.insert(terrainId, dto.name(), geoJson);
        } catch (DataIntegrityViolationException e) {
            throw mapIntegrityException(e, dto.name());
        }
    }

    @Transactional
    public void update(UUID terrainId, UUID parcelId, ParcelUpdateRequest dto) {
        ensureTerrainExists(terrainId);
        // valida que la parcela existe y pertenece al terreno
        parcelRepository.getParcel(terrainId, parcelId, "id");

        if (dto.name() != null && !dto.name().isBlank()) {
            if (parcelRepository.nameExistsInTerrain(terrainId, dto.name(), parcelId)) {
                throw new DuplicateParcelNameException(
                        i18nService.getMessage("parcel.name.duplicate", dto.name())
                );
            }
            try {
                parcelRepository.updateName(terrainId, parcelId, dto.name());
            } catch (DataIntegrityViolationException e) {
                throw mapIntegrityException(e, dto.name());
            }
        }

        if (dto.geometry() != null && !dto.geometry().isEmpty()) {
            String geoJson = serializeGeometry(dto.geometry());
            validateContainmentAndOverlap(terrainId, geoJson, parcelId);
            try {
                parcelRepository.updateGeometry(terrainId, parcelId, geoJson);
            } catch (DataIntegrityViolationException e) {
                throw mapIntegrityException(e, null);
            }
        }
    }

    @Transactional
    public void delete(UUID terrainId, UUID parcelId) {
        // Si no existe, getParcel lanzara ParcelNotFoundException (404).
        parcelRepository.getParcel(terrainId, parcelId, "id");
        parcelRepository.deleteParcel(terrainId, parcelId);
        eventPublisher.publishParcelDeleted(new ParcelDeletedEvent(parcelId, terrainId));
    }

    /**
     * HU-TER-04 §3.7: emite un {@code parcel-deleted} por cada parcela del
     * terreno antes de que el cascade borre las filas. Lo invoca
     * {@link TerrainService#deleteTerrain} y {@link TerrainService#deleteTerrainsByUserId}.
     */
    @Transactional
    public void publishCascadeDeletesForTerrain(UUID terrainId) {
        for (UUID parcelId : parcelRepository.findIdsByTerrainId(terrainId)) {
            eventPublisher.publishParcelDeleted(new ParcelDeletedEvent(parcelId, terrainId));
        }
    }

    /** Resumen para la ficha del terreno: parcel_count y suma de areas. */
    @Transactional(readOnly = true)
    public Map<String, Object> getParcelsSummary(UUID terrainId) {
        return parcelRepository.getParcelsSummary(terrainId);
    }

    public boolean existsById(UUID id) {
        return parcelRepository.existsById(id);
    }

    private void ensureTerrainExists(UUID terrainId) {
        if (!terrainRepository.existsById(terrainId)) {
            throw new TerrainNotFoundException(
                    i18nService.getMessage("terrain.notfound", terrainId)
            );
        }
    }

    private String serializeGeometry(Map<String, Object> geometry) {
        try {
            return mapper.writeValueAsString(geometry);
        } catch (JsonProcessingException e) {
            throw new InvalidGeometryException(i18nService.getMessage("error.geojson"), e);
        }
    }

    private void validateContainmentAndOverlap(UUID terrainId, String geoJson, UUID excludeParcelId) {
        try {
            if (!parcelRepository.isWithinTerrain(terrainId, geoJson)) {
                throw new ParcelNotWithinTerrainException(
                        i18nService.getMessage("parcel.not.within.terrain")
                );
            }
        } catch (DataIntegrityViolationException e) {
            // ST_GeomFromGeoJSON puede fallar si el GeoJSON esta malformado.
            throw new InvalidGeometryException(
                    i18nService.getMessage("parcel.geometry.invalid"), e
            );
        }
        List<String> overlapping = parcelRepository.findOverlapping(terrainId, geoJson, excludeParcelId);
        if (!overlapping.isEmpty()) {
            throw new ParcelOverlapException(
                    i18nService.getMessage("parcel.overlaps", overlapping.get(0))
            );
        }
    }

    private RuntimeException mapIntegrityException(DataIntegrityViolationException e, String name) {
        String cause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
        if (cause != null && cause.contains("parcel_name_unique_per_terrain")) {
            return new DuplicateParcelNameException(
                    i18nService.getMessage("parcel.name.duplicate", name == null ? "?" : name)
            );
        }
        if (cause != null && (cause.contains("parcel_geom_valid") || cause.contains("parcel_geom_srid"))) {
            return new InvalidGeometryException(i18nService.getMessage("parcel.geometry.invalid"));
        }
        return e;
    }

    /** Asegura que getParcel(...) lance {@link ParcelNotFoundException} si falta. */
    @SuppressWarnings("unused")
    private void requireExists(UUID terrainId, UUID parcelId) {
        parcelRepository.getParcel(terrainId, parcelId, "id");
    }
}
