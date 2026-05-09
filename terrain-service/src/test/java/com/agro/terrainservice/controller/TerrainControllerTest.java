package com.agro.terrainservice.controller;

import com.agro.terrainservice.dto.TerrainRequest;
import com.agro.terrainservice.exception.AreaOutOfRangeException;
import com.agro.terrainservice.exception.GlobalExceptionHandler;
import com.agro.terrainservice.exception.InvalidFieldException;
import com.agro.terrainservice.exception.InvalidGeometryException;
import com.agro.terrainservice.exception.TerrainNotFoundException;
import com.agro.terrainservice.exception.UserNotFoundException;
import com.agro.terrainservice.service.CadastralImportService;
import com.agro.terrainservice.service.I18nService;
import com.agro.terrainservice.service.TerrainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TerrainController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class TerrainControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TerrainService terrainService;
    @MockitoBean private I18nService i18nService;
    @MockitoBean private CadastralImportService cadastralImportService;

    private static final String VALID_GEOMETRY_JSON = """
            {"type":"Polygon","coordinates":[[[-3.71,40.42],[-3.71,40.4209],[-3.7088,40.4209],[-3.7088,40.42],[-3.71,40.42]]]}
            """;

    @Test
    @DisplayName("TER-1.01 - Happy path minimo - solo campos obligatorios")
    void getTerrains_returnsList_whenUserIdProvided() throws Exception {
        UUID userId = UUID.randomUUID();
        when(terrainService.getTerrains(any(UUID.class), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/terrain").param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("TER-2.05 - Detalle existente")
    void getTerrain_returnsTerrain_whenExists() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("name", "Parcela Norte");
        when(terrainService.getTerrain(any(UUID.class), any())).thenReturn(body);

        mockMvc.perform(get("/terrain/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("TER-1.01 - Happy path 201 con id y mensaje")
    void create_returns201_withIdAndMessage() throws Exception {
        UUID newId = UUID.randomUUID();
        Map<String, Object> geometry = Map.of(
                "type", "Polygon",
                "coordinates", Collections.emptyList()
        );
        TerrainRequest request = new TerrainRequest(
                "Nuevo Terreno",
                UUID.randomUUID(),
                geometry,
                null, null, null, null
        );

        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("Terreno creado");

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newId.toString()))
                .andExpect(jsonPath("$.message").value("Terreno creado"));
    }

    @Test
    @DisplayName("TER-1.03 - name ausente / vacio dispara 400 terrain.name.required")
    void create_returns400_whenNameMissing() throws Exception {
        String body = """
                {"name":"","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wrong payload"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.name.required"))));
    }

    @Test
    @DisplayName("TER-1.04 - name > 255 chars dispara 400 terrain.name.too.long")
    void create_returns400_whenNameTooLong() throws Exception {
        String longName = "x".repeat(256);
        String body = """
                {"name":"%s","user_id":"%s","geometry":%s}
                """.formatted(longName, UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wrong payload"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.name.too.long"))));
    }

    @Test
    @DisplayName("TER-1.05 - user_id ausente dispara 400 terrain.user_id.required")
    void create_returns400_whenUserIdMissing() throws Exception {
        String body = """
                {"name":"X","geometry":%s}
                """.formatted(VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wrong payload"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.user_id.required"))));
    }

    @Test
    @DisplayName("TER-1.06 - user_id no UUID dispara 400 Wrong payload")
    void create_returns400_whenUserIdMalformed() throws Exception {
        String body = """
                {"name":"X","user_id":"abc","geometry":%s}
                """.formatted(VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-1.07 - user_id inexistente en auth-service dispara 404 user.notfound")
    void create_returns404_whenUserNotFound() throws Exception {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new UserNotFoundException("user.notfound"));

        UUID uid = UUID.randomUUID();
        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(uid, VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.detail").value(containsString("user.notfound")));
    }

    @Test
    @DisplayName("TER-1.08 - geometry ausente / vacia dispara 400 terrain.geometry.required")
    void create_returns400_whenGeometryMissing() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":{}}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wrong payload"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.geometry.required"))));
    }

    @Test
    @DisplayName("TER-1.09 - geometry no serializable dispara 400 Invalid geometry")
    void create_returns400_whenGeometryInvalidJson() throws Exception {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new InvalidGeometryException("error.geojson"));

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid geometry"))
                .andExpect(jsonPath("$.detail").value(containsString("error.geojson")));
    }

    @Test
    @DisplayName("TER-1.10 - geometria con SRID != 4326 dispara 400 Invalid geometry")
    void create_returns400_whenGeometryWrongSrid() throws Exception {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new InvalidGeometryException("terrain.geometry.invalid"));

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid geometry"))
                .andExpect(jsonPath("$.detail").value(containsString("terrain.geometry.invalid")));
    }

    @Test
    @DisplayName("TER-1.12 - area < 100 m^2 dispara 400 Area out of range")
    void create_returns400_whenAreaTooSmall() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new AreaOutOfRangeException("terrain.area.out.of.range"));

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Area out of range"))
                .andExpect(jsonPath("$.detail").value(containsString("terrain.area.out.of.range")));
    }

    @Test
    @DisplayName("TER-1.14 - soil_type fuera del enum dispara 400")
    void create_returns400_whenSoilTypeInvalid() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"soil_type":"plastico"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-1.15 - irrigation fuera del enum dispara 400")
    void create_returns400_whenIrrigationInvalid() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"irrigation":"manguera"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-1.16 - slope_percent < 0 dispara 400 terrain.slope.invalid")
    void create_returns400_whenSlopeBelowZero() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"slope_percent":-0.1}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.slope.invalid"))));
    }

    @Test
    @DisplayName("TER-1.17 - slope_percent > 100 dispara 400 terrain.slope.invalid")
    void create_returns400_whenSlopeAbove100() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"slope_percent":100.01}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.slope.invalid"))));
    }

    @Test
    @DisplayName("TER-1.18 - slope_percent en frontera 0 acepta 201")
    void create_accepts_slopeZero() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("ok");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"slope_percent":0.0}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TER-1.18 - slope_percent en frontera 100 acepta 201")
    void create_accepts_slope100() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("ok");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"slope_percent":100.0}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TER-1.19 - cadastral_ref mal formada (corta) dispara 400")
    void create_returns400_whenCadastralRefTooShort() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"cadastral_ref":"abc"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.cadastral_ref.malformed"))));
    }

    @Test
    @DisplayName("TER-1.20 - cadastral_ref con minusculas dispara 400")
    void create_returns400_whenCadastralRefLowercase() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"cadastral_ref":"abcd1234efghij"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.cadastral_ref.malformed"))));
    }

    @Test
    @DisplayName("TER-1.21 - cadastral_ref 14 chars valida acepta 201")
    void create_accepts_cadastralRef14() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("ok");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"cadastral_ref":"1234ABCD5678EF"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TER-1.22 - cadastral_ref 20 chars valida acepta 201")
    void create_accepts_cadastralRef20() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("ok");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"cadastral_ref":"9872023VH5797S0001WX"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TER-1.23 - cadastral_ref 21 chars dispara 400")
    void create_returns400_whenCadastralRefTooLong() throws Exception {
        String body = """
                {"name":"X","user_id":"%s","geometry":%s,"cadastral_ref":"1234ABCD5678EF1234567"}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("terrain.cadastral_ref.malformed"))));
    }

    @Test
    @DisplayName("TER-1.24 - Content-Type no JSON dispara 415")
    void create_returns415_whenContentTypeWrong() throws Exception {
        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("TER-1.25 - Body JSON malformado dispara 400")
    void create_returns400_whenBodyMalformed() throws Exception {
        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-1.30 - Mensaje en ingles cuando Accept-Language=en")
    void create_message_inEnglish_whenAcceptLanguageEn() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any()))
                .thenReturn("Terrain named X created successfully.");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(containsString("created successfully")));
    }

    // -------- Section 2: GET /terrain --------

    @Test
    @DisplayName("TER-2.01 - Listar sin terrenos del usuario devuelve []")
    void list_returnsEmpty_whenNoTerrains() throws Exception {
        UUID userId = UUID.randomUUID();
        when(terrainService.getTerrains(any(UUID.class), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/terrain").param("user_id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("TER-2.03 - GET /terrain sin user_id dispara 400")
    void list_returns400_whenUserIdMissing() throws Exception {
        mockMvc.perform(get("/terrain"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-2.04 - GET /terrain con user_id mal formado dispara 400 Illegal argument")
    void list_returns400_whenUserIdMalformed() throws Exception {
        mockMvc.perform(get("/terrain").param("user_id", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-2.06 - Detalle inexistente dispara 404 Terrain not found")
    void detail_returns404_whenNotFound() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        when(terrainService.getTerrain(eq(id), any()))
                .thenThrow(new TerrainNotFoundException("terrain.notfound"));

        mockMvc.perform(get("/terrain/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"))
                .andExpect(jsonPath("$.detail").value(containsString("terrain.notfound")));
    }

    @Test
    @DisplayName("TER-2.07 - Detalle con UUID malformado dispara 400")
    void detail_returns400_whenIdMalformed() throws Exception {
        mockMvc.perform(get("/terrain/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    // -------- Section 3: DELETE /terrain --------

    @Test
    @DisplayName("TER-3.01 - DELETE owner devuelve 204")
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doNothing().when(terrainService).deleteTerrain(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TER-3.02 - DELETE por usuario equivocado dispara 404")
    void delete_returns404_whenWrongOwner() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new TerrainNotFoundException("terrain.notfound"))
                .when(terrainService).deleteTerrain(eq(id), eq(userId));

        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Terrain not found"));
    }

    @Test
    @DisplayName("TER-3.03 - DELETE id inexistente dispara 404")
    void delete_returns404_whenIdMissing() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new TerrainNotFoundException("terrain.notfound"))
                .when(terrainService).deleteTerrain(eq(id), eq(userId));

        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TER-3.05 - DELETE sin user_id dispara 400")
    void delete_returns400_whenUserIdMissing() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/terrain/{id}", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-3.06 - DELETE con user_id mal formado dispara 400")
    void delete_returns400_whenUserIdMalformed() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-3.07 - DELETE idempotente: primero 204, segundo 404")
    void delete_idempotency() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Primer DELETE: ok
        doNothing().when(terrainService).deleteTerrain(eq(id), eq(userId));
        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNoContent());

        // Segundo DELETE: 404
        doThrow(new TerrainNotFoundException("terrain.notfound"))
                .when(terrainService).deleteTerrain(eq(id), eq(userId));
        mockMvc.perform(delete("/terrain/{id}", id).param("user_id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    // -------- Section 4: fields= projection --------

    @Test
    @DisplayName("TER-4.06 - fields= con campo desconocido dispara 400 Invalid field")
    void fields_returns400_whenUnknownField() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/terrain/{id}", id).param("fields", "password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-4.07 - fields= con caso mixto NAME dispara 400 Invalid field")
    void fields_returns400_whenUppercase() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/terrain/{id}", id).param("fields", "NAME"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-4.11 - fields= con inyeccion SQL dispara 400")
    void fields_returns400_whenSqlInjectionAttempt() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/terrain/{id}", id).param("fields", "id;DROP TABLE terrain"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-4.02 - fields=name devuelve solo esa clave")
    void fields_singleField() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("name", "X");
        when(terrainService.getTerrain(eq(id), any())).thenReturn(body);

        mockMvc.perform(get("/terrain/{id}", id).param("fields", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X"));
    }

    @Test
    @DisplayName("TER-4.03 - fields=id,name,area_m2 devuelve esas 3 claves")
    void fields_multipleFields() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("name", "X");
        body.put("area_m2", 1234.0);
        when(terrainService.getTerrain(eq(id), any())).thenReturn(body);

        mockMvc.perform(get("/terrain/{id}", id).param("fields", "id,name,area_m2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("X"))
                .andExpect(jsonPath("$.area_m2").value(1234.0));
    }

    @Test
    @DisplayName("TER-4.08 - fields= vacio se trata como sin fields")
    void fields_empty() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        when(terrainService.getTerrain(eq(id), any())).thenReturn(body);

        mockMvc.perform(get("/terrain/{id}", id).param("fields", ""))
                .andExpect(status().isOk());
    }

    // -------- Section 9: POST /terrain/import --------

    @Test
    @DisplayName("TER-1.01 import - Happy path: 200 con sugerencia mapeada")
    void importCadastral_returns200_withSuggestion() throws Exception {
        com.agro.terrainservice.dto.CadastralImportResponse suggestion =
                new com.agro.terrainservice.dto.CadastralImportResponse(
                        "1234ABCDEFGHIJKL5678",
                        "Parcela Catastro",
                        Map.of("type", "Polygon", "coordinates", java.util.List.of()),
                        12345.6,
                        "Agricola",
                        "TA",
                        "Almeria",
                        "Almeria");
        when(cadastralImportService.fetch(any())).thenReturn(suggestion);

        String body = """
                {"reference":"1234ABCDEFGHIJKL5678","kind":"CADASTRAL"}
                """;

        mockMvc.perform(post("/terrain/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("1234ABCDEFGHIJKL5678"))
                .andExpect(jsonPath("$.suggested_name").value("Parcela Catastro"));
    }

    @Test
    @DisplayName("TER-9.01 - import sin reference dispara 400")
    void importCadastral_returns400_whenReferenceMissing() throws Exception {
        String body = """
                {"reference":"","kind":"CADASTRAL"}
                """;

        mockMvc.perform(post("/terrain/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-9.02 - import sin kind dispara 400 (Jackson)")
    void importCadastral_returns400_whenKindMissing() throws Exception {
        String body = """
                {"reference":"1234ABCDEFGHIJKL5678"}
                """;

        mockMvc.perform(post("/terrain/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TER-9.03 - import con kind invalido dispara 400 (Jackson)")
    void importCadastral_returns400_whenKindInvalid() throws Exception {
        String body = """
                {"reference":"1234ABCDEFGHIJKL5678","kind":"OTRO"}
                """;

        mockMvc.perform(post("/terrain/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------- Section 14: transversal i18n / problem detail --------

    @Test
    @DisplayName("TER-14.04 - ProblemDetail content-type es application/problem+json")
    void error_contentType_isProblemJson() throws Exception {
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        when(terrainService.getTerrain(eq(id), any()))
                .thenThrow(new TerrainNotFoundException("terrain.notfound"));

        mockMvc.perform(get("/terrain/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("TER-14.05 - ProblemDetail trae type, title, status, detail (RFC 7807)")
    void error_problemDetail_hasRfc7807Fields() throws Exception {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new UserNotFoundException("user.notfound"));

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("TER-14.06 - Validation errors aparecen como array")
    void error_validation_returnsErrorsArray() throws Exception {
        String body = """
                {"name":"","geometry":%s}
                """.formatted(VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        containsString("terrain.name.required"),
                        containsString("terrain.user_id.required")
                )));
    }

    @Test
    @DisplayName("TER-14.07 - Mensaje no expone stack trace")
    void error_doesNotExposeStackTrace() throws Exception {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(terrainService.create(any(TerrainRequest.class)))
                .thenThrow(new UserNotFoundException("user.notfound"));

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.class").doesNotExist());
    }

    @Test
    @DisplayName("TER-14.08 - 201 trae Content-Type: application/json")
    void successResponse_contentType_isJson() throws Exception {
        UUID newId = UUID.randomUUID();
        when(terrainService.create(any(TerrainRequest.class))).thenReturn(newId);
        when(i18nService.getMessage(eq("terrain.created"), any())).thenReturn("ok");

        String body = """
                {"name":"X","user_id":"%s","geometry":%s}
                """.formatted(UUID.randomUUID(), VALID_GEOMETRY_JSON);

        mockMvc.perform(post("/terrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("TER-4.06 invalid field: 400 - delegated to controller layer")
    void fields_handlesInvalidFieldException() throws Exception {
        // Si el service lanza InvalidFieldException directamente
        when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        UUID id = UUID.randomUUID();
        when(terrainService.getTerrain(eq(id), any()))
                .thenThrow(new InvalidFieldException("Invalid field: foo"));

        mockMvc.perform(get("/terrain/{id}", id).param("fields", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid field"));
    }
}
