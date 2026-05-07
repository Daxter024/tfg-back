package com.agro.terrainservice.service;

import com.agro.terrainservice.client.UserGrpcClient;
import com.agro.terrainservice.constants.TerrainFields;
import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.event.TerrainDeletedEvent;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.UserNotFoundException;
import com.agro.terrainservice.repository.TerrainRepository;
import com.agro.terrainservice.utils.FieldsValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TerrainService {

    /** Area minima admitida (0,01 ha = 100 m^2). HU-TER-01. */
    static final double MIN_AREA_M2 = 100.0;
    /** Area maxima admitida (10 000 ha = 1e8 m^2). HU-TER-01. */
    static final double MAX_AREA_M2 = 100_000_000.0;

    private final TerrainRepository terrainRepository;
    private final I18nService i18nService;
    private final ObjectMapper mapper;
    private final FieldsValidator fieldsValidator;
    private final UserGrpcClient userGrpcClient;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Map<String, Object> getTerrain(UUID id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return terrainRepository.getTerrain(id, selectedFields);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTerrains(UUID user_id, List<TerrainFields> fields) {
        String selectedFields = fieldsValidator.formatFieldList(fields);
        return terrainRepository.getTerrains(user_id, selectedFields);
    }

    /**
     * HU-TER-01: crea un terreno con campos descriptivos opcionales.
     *
     * <p>Reglas:
     * <ul>
     *   <li>Valida la existencia del usuario contra {@code auth-service} (gRPC).</li>
     *   <li>Serializa la geometria a GeoJSON; rechaza payloads no serializables.</li>
     *   <li>Persiste invocando a {@code ST_GeomFromGeoJSON}; PostGIS chequea
     *       SRID y validez. Si el area calculada cae fuera del rango admitido la
     *       constraint CHECK la rechaza, y el handler la traduce a 400.</li>
     * </ul>
     */
    @Transactional
    public UUID create(TerrainRequest dto) {
        if (!userGrpcClient.validateUser(dto.user_id())) {
            throw new UserNotFoundException(
                    i18nService.getMessage("user.notfound", dto.user_id())
            );
        }

        String geoJson;
        try {
            geoJson = mapper.writeValueAsString(dto.geometry());
        } catch (JsonProcessingException e) {
            throw new InvalidGeometryException(i18nService.getMessage("error.geojson"), e);
        }

        try {
            return terrainRepository.saveWithCalculations(
                    dto.name(),
                    dto.user_id(),
                    geoJson,
                    dto.soil_type(),
                    dto.slope_percent(),
                    dto.irrigation(),
                    dto.cadastral_ref()
            );
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Diferenciar entre violacion de rango de area (HU-TER-01) y otras
            // violaciones (geometria invalida, SRID, etc.) por el texto de la causa.
            String cause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
            if (cause != null && cause.contains("terrain_area_range")) {
                throw new AreaOutOfRangeException(
                        i18nService.getMessage("terrain.area.out.of.range", MIN_AREA_M2, MAX_AREA_M2)
                );
            }
            if (cause != null && (cause.contains("terrain_geom_valid") || cause.contains("terrain_geom_srid"))) {
                throw new InvalidGeometryException(i18nService.getMessage("terrain.geometry.invalid"));
            }
            throw e;
        }
    }

    @Transactional
    public void deleteTerrain(UUID id, UUID userId) {
        terrainRepository.deleteTerrain(id, userId);
        eventPublisher.publishTerrainDeleted(new TerrainDeletedEvent(id));
    }

    @Transactional
    public void deleteTerrainsByUserId(UUID userId) {
        List<UUID> terrainIds = terrainRepository.findIdsByUserId(userId);
        for (UUID id : terrainIds) {
            terrainRepository.deleteById(id);
            eventPublisher.publishTerrainDeleted(new TerrainDeletedEvent(id));
        }
    }

    /** Helpers de paquete para validacion de propietario en HU-TER-03/04. */
    @Transactional(readOnly = true)
    public boolean existsForUser(UUID terrainId, UUID userId) {
        try {
            Map<String, Object> row = terrainRepository.getTerrain(terrainId, "id, user_id");
            Object owner = row.get("user_id");
            return owner != null && userId.toString().equalsIgnoreCase(owner.toString());
        } catch (Exception e) {
            return false;
        }
    }
}
