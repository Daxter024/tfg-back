# `season-service` — Plan de tests exhaustivo

> Documento de referencia para que un desarrollador (humano o agente IA) implemente y verifique **todos** los casos de comportamiento del microservicio `season-service`. Este plan es el equivalente al `crop-service-test-plan.md` y al `terrain-service-test-plan.md`, adaptado al dominio de temporadas.
>
> **Audiencia:** desarrollador que va a aterrizar la suite completa de tests.
>
> **Fuentes de verdad consultadas para construir este plan:**
> - `season-service/SEASON-SERVICE-DOCUMENTATION.md` (contratos REST, gRPC, Kafka, esquema BBDD).
> - `season-service/SEASON_SERVICE_IMPLEMENTATION_PLAN.md` (estado del código tras los fixes SS-1..SS-10).
> - `season-service/INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` (modelo de dominio y deuda de propiedad).
> - Código actual en `feat-season-service`.
>
> **Identificadores estables:** cada caso lleva un ID `SEASON-XX.YY`. Úsalo en `@DisplayName` y en commits/PRs para trazar 1:1 plan ↔ JUnit ↔ script.
>
> **Estado de cada caso:**
> - ✅ ya cubierto en `feat-season-service` (commit `35d52e4`).
> - 🟡 parcialmente cubierto (falta capa o caso límite).
> - ❌ no cubierto.
> - 🚧 requiere implementar funcionalidad previa.

---

## Índice

- [0. Convenciones y plantilla común](#0-convenciones-y-plantilla-común)
- [1. Tests de `POST /season` — alta de season](#1-tests-de-post-season--alta-de-season)
- [2. Tests de `GET /season/{id}` — detalle](#2-tests-de-get-seasonid--detalle)
- [3. Tests de `GET /season/terrain/{terrainId}` — listar por terreno](#3-tests-de-get-seasonterrainterrainid--listar-por-terreno)
- [4. Tests de `DELETE /season/{id}` — borrado manual](#4-tests-de-delete-seasonid--borrado-manual)
- [5. Tests de los gRPC clients](#5-tests-de-los-grpc-clients)
- [6. Tests del listener Kafka `terrain-deleted`](#6-tests-del-listener-kafka-terrain-deleted)
- [7. Tests del repositorio (BBDD real con Testcontainers)](#7-tests-del-repositorio-bbdd-real-con-testcontainers)
- [8. Tests de integración cross-service](#8-tests-de-integración-cross-service)
- [9. Tests transversales (i18n, ProblemDetail, headers, locales)](#9-tests-transversales-i18n-problemdetail-headers-locales)
- [10. Tests de seguridad (defensivos)](#10-tests-de-seguridad-defensivos)
- [11. Tests del bloque "ownership" (NUEVO — bloque 🚧)](#11-tests-del-bloque-ownership-nuevo--bloque-)
- [Apéndice A — Matriz de cobertura por capa](#apéndice-a--matriz-de-cobertura-por-capa)
- [Apéndice B — Fixtures listas para copiar](#apéndice-b--fixtures-listas-para-copiar)
- [Apéndice C — Orden de implementación sugerido](#apéndice-c--orden-de-implementación-sugerido)

---

## 0. Convenciones y plantilla común

### 0.1 Capas de test

| Capa | Tecnología | Cuándo usarla |
|---|---|---|
| **Unit** | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Lógica del service y excepciones. Todos los colaboradores mockeados. |
| **WebMvc slice** | `MockMvcBuilders.standaloneSetup(controller)` + Mockito | Status HTTP, mapping JSON, headers, validación de request. El service va mockeado. |
| **JDBC slice** | `@JdbcTest + @Import(SeasonRepository.class)` con Testcontainers PostgreSQL | SQL real, mapeos, constraints. Nada de servlets. |
| **gRPC unit** | Mock del stub blocking + `@InjectMocks` | Verifica que el cliente envuelve correctamente el `Exception` en `RuntimeException`. |
| **Kafka unit** | Mock del service + invocación directa del listener | Smoke; el listener delega correctamente. |
| **Integración full** | `@SpringBootTest(webEnvironment=RANDOM_PORT) + @Testcontainers` | Flyway + JdbcTemplate + listener Kafka real (con `@EmbeddedKafka` o broker Testcontainers). |
| **Cross-service** | `@SpringBootTest` + WireMock para gRPC + Testcontainers Kafka | Flujo end-to-end POST /season → CheckTerrainExists/CheckCropExists → INSERT. |
| **Contract test** | snapshot del `.proto` y de los stubs | Verifica que el cliente sigue compatible si el servidor remoto cambia. |

### 0.2 Plantilla de aserciones para errores

Toda respuesta de error debe usar `ProblemDetail` (RFC 7807):

```java
mockMvc.perform(...)
    .andExpect(status().is(<status>))
    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
    .andExpect(jsonPath("$.status").value(<status>))
    .andExpect(jsonPath("$.title").value("<Title>"))
    .andExpect(jsonPath("$.detail").value(containsString("<i18n key o trozo>")));
```

### 0.3 Mensajes i18n

- En **unit tests**, mockear `I18nService` para que devuelva la propia clave: `when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));`. Asserts contra claves (`season.terrain.notfound`), no contra texto traducido.
- En **WebMvc/integración**, alternar `Accept-Language: es` y `Accept-Language: en` y verificar que el `LocaleResolver` resuelve correctamente.

### 0.4 Estado inicial supuesto

Antes de cada test (donde aplique):

1. Tabla `season` truncada (no `season_type` — los seeds del V1 deben mantenerse para FK).
2. Mocks de `SeasonRepository`, `TerrainGrpcClient`, `CropGrpcClient`, `I18nService` reseteados (`@BeforeEach`).
3. Para tests de listener Kafka: stub regenerado, no compartido entre suites paralelas.
4. Para tests cross-service: WireMock reset, contadores limpios.

### 0.5 IDs de test y nomenclatura de archivos

| Sección | Prefijo | Archivo Java sugerido |
|---|---|---|
| 1 — POST | `SEASON-1.NN` | `controller/SeasonControllerCreateTest.java` (o sub-clase de `SeasonControllerTest.java`) |
| 2 — GET detail | `SEASON-2.NN` | `controller/SeasonControllerDetailTest.java` |
| 3 — GET by terrain | `SEASON-3.NN` | `controller/SeasonControllerListByTerrainTest.java` |
| 4 — DELETE | `SEASON-4.NN` | `controller/SeasonControllerDeleteTest.java` |
| 5 — gRPC clients | `SEASON-5.NN` | `grpc/TerrainGrpcClientTest.java`, `grpc/CropGrpcClientTest.java` |
| 6 — Kafka listener | `SEASON-6.NN` | `listener/TerrainDeletedListenerTest.java` (existente) + `listener/TerrainDeletedListenerKafkaIT.java` (`@EmbeddedKafka`) |
| 7 — Repo (Testcontainers) | `SEASON-7.NN` | `repository/SeasonRepositoryIT.java` |
| 8 — Integración cross-service | `SEASON-8.NN` | `integration/SeasonE2EIntegrationTest.java` |
| 9 — Transversal | `SEASON-9.NN` | `transversal/SeasonI18nTest.java`, `transversal/SeasonProblemDetailTest.java` |
| 10 — Seguridad | `SEASON-10.NN` | `security/SeasonSecurityTest.java` |
| 11 — Ownership (futuro) | `SEASON-11.NN` | `ownership/SeasonOwnershipTest.java` (🚧) |

> Cada `@DisplayName` debe contener el ID. Ejemplo: `@DisplayName("SEASON-1.01: happy path mínimo")`.

---

## 1. Tests de `POST /season` — alta de season

> **Endpoint bajo prueba:** `POST /season`. **Body:** `SeasonRequest{ terrain_id, crop_id, start_date, end_date?, season_type_id?, observations? }`. **Validación:** Bean Validation + `@AssertTrue` + gRPC `CheckTerrainExists` + gRPC `CheckCropExists`. **Estado HTTP de éxito:** 201 con `UUID` en JSON.

| Caso | ID | Request / Pre-condición | Resultado esperado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path mínimo (solo obligatorios) | SEASON-1.01 | `{terrain_id, crop_id, start_date}` con UUIDs válidos; mocks gRPC → true | 201; body `"<uuid>"`; fila persistida | WebMvc + JDBC | ✅ (parcial: ya en `SeasonControllerTest.createSeason_happyPath`) |
| Happy path completo | SEASON-1.02 | añade `end_date`, `season_type_id=1`, `observations` | 201 | WebMvc + JDBC | ❌ |
| Happy path con `Accept-Language: en` | SEASON-1.03 | header EN; mocks ok | 201 (el body es solo `UUID`, no afecta) | WebMvc | ❌ |
| `terrain_id` ausente | SEASON-1.04 | body sin `terrain_id` | 400; `errors` contiene `"terrain_id: El identificador del terreno es obligatorio"` | WebMvc | ✅ (cubierto por `createSeason_invalidBody_returns400WithErrors`) |
| `terrain_id` no UUID | SEASON-1.05 | `"terrain_id":"abc"` | 400 (Jackson deserialización) | WebMvc | ❌ |
| `crop_id` ausente | SEASON-1.06 | body sin `crop_id` | 400; `errors` contiene `"crop_id"` | WebMvc | ✅ (parcial; mismo test que 1.04 cubre body vacío) |
| `crop_id` no UUID | SEASON-1.07 | `"crop_id":"abc"` | 400 | WebMvc | ❌ |
| `start_date` ausente | SEASON-1.08 | body sin `start_date` | 400; `errors` contiene `"start_date"` | WebMvc | ✅ (parcial) |
| `start_date` formato incorrecto | SEASON-1.09 | `"start_date":"01/01/2025"` (DD/MM/YYYY) | 400 (Jackson rechaza) | WebMvc | ❌ |
| `end_date < start_date` | SEASON-1.10 | start=2025-08-01, end=2025-03-01 | 400; `errors` contiene mensaje de `season.dates.range` | WebMvc | ✅ (`createSeason_endBeforeStart_returns400`) |
| `end_date == start_date` (borde, válido) | SEASON-1.11 | start=2025-03-01, end=2025-03-01 | 201 | WebMvc + JDBC | ❌ |
| `end_date` ausente (válido) | SEASON-1.12 | solo `start_date` | 201; persiste con `end_date NULL` | WebMvc + JDBC | ❌ |
| `observations` > 2000 chars | SEASON-1.13 | string de 2001 chars | 400; `errors` contiene `"observations"` | WebMvc | ❌ |
| `observations` exactamente 2000 chars | SEASON-1.14 | borde superior | 201 | WebMvc + JDBC | ❌ |
| `observations` con UTF-8 | SEASON-1.15 | `"observations":"Año 2025: cosecha de arándanos 🌱"` | 201; round-trip preserva los chars | JDBC | ❌ |
| `season_type_id` válido | SEASON-1.16 | `"season_type_id":1..4` | 201 | WebMvc + JDBC | ❌ |
| `season_type_id` inexistente | SEASON-1.17 | `"season_type_id":99` | 500 hoy (FK violation no manejada) o 400 si se mejora handler | WebMvc + JDBC | ❌ |
| `season_type_id` ausente | SEASON-1.18 | sin el campo | 201; persiste con NULL | WebMvc + JDBC | ❌ |
| `terrain_id` no existe en `terrain-service` | SEASON-1.19 | mock `checkTerrainExists` → false | 404; `title:"Terrain not found"`; **sin** llamar a crop-service ni al repo | WebMvc | ✅ (`createSeason_terrainNotFound_returns404WithTitle`) |
| `crop_id` no existe en `crop-service` | SEASON-1.20 | mock `checkCropExists` → false | 404; `title:"Crop not found"`; sin llamar al repo | WebMvc | ✅ (`createSeason_cropNotFound_returns404WithTitle`) |
| `terrain-service` caído (gRPC error) | SEASON-1.21 | mock lanza `StatusRuntimeException(UNAVAILABLE)` | 500; el cliente envuelve en `RuntimeException` | WebMvc | ❌ |
| `crop-service` caído (gRPC error) | SEASON-1.22 | mismo patrón | 500 | WebMvc | ❌ |
| Orden de validación: terrain primero | SEASON-1.23 | ambos mocks → false | el RPC de crop **NO** se invoca; throw `TerrainNotFoundException` | unit | ✅ (`createSeason_terrainCheckedBeforeCrop`) |
| Body vacío | SEASON-1.24 | `{}` | 400; `errors` con 3 entradas (terrain, crop, start) | WebMvc | ✅ (`createSeason_invalidBody_returns400WithErrors`) |
| Body no JSON | SEASON-1.25 | `Content-Type: text/plain` | 415 | WebMvc | ❌ |
| Body JSON malformado | SEASON-1.26 | `'{"terrain_id":'` | 400 | WebMvc | ❌ |
| Idempotencia: dos POST iguales | SEASON-1.27 | dos requests con mismo body válido | 201 + 201 (no hay UNIQUE constraint); 2 filas con UUID distinto | JDBC | ❌ |
| Inserción concurrente | SEASON-1.28 | 5 threads creando con mismo body | 5 filas insertadas con UUIDs únicos | JDBC + concurrency | ❌ |
| INSERT después del check pasa pero `terrain` se borra a la vez (TOCTOU) | SEASON-1.29 | mocks ok pero el repo lanza `DataIntegrityViolation` (caso teórico) | 500 hoy; documentar como race conocida | unit + integración | ❌ |

### 1.A Asserts adicionales en happy path (SEASON-1.01)

```java
// Tras 201, verificar persistencia con un query directo
List<Map<String,Object>> rows = jdbcTemplate.queryForList(
    "SELECT id, terrain_id, crop_id, start_date, end_date FROM season WHERE id = ?",
    UUID.fromString(returnedUuid));
assertThat(rows).hasSize(1);
assertThat(rows.get(0).get("terrain_id")).isEqualTo(terrainId);
assertThat(rows.get(0).get("crop_id")).isEqualTo(cropId);
```

---

## 2. Tests de `GET /season/{id}` — detalle

| Caso | ID | Request / Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Detalle existente sin `fields` | SEASON-2.01 | seed 1 fila; GET por id | 200; mapa con 7 columnas | WebMvc + JDBC | ✅ (`getSeason_returnsMap`, parcial — solo verifica `id`) |
| Detalle inexistente | SEASON-2.02 | UUID aleatorio | 404; `title:"Resource not found"` | WebMvc | ❌ (cubre `deleteSeason_unknownId_returns404` el handler, no este endpoint) |
| Detalle con `fields=id,start_date` | SEASON-2.03 | seed 1; GET con proyección | 200; mapa con solo 2 keys | WebMvc + JDBC | ❌ |
| `fields` con valor fuera del enum | SEASON-2.04 | `?fields=secret` | 400 (binding error de `List<SeasonField>`) | WebMvc | ❌ |
| `fields` mezcla válidos+inválidos | SEASON-2.05 | `?fields=id,secret` | 400 | WebMvc | ❌ |
| `fields` con duplicados | SEASON-2.06 | `?fields=id,id,start_date` | 200 (Spring binding deduplica al construir `List<Enum>`) | WebMvc | ❌ |
| `fields` vacío | SEASON-2.07 | `?fields=` | 200; equivale a `*` | WebMvc | ❌ |
| `fields=geometry` (campo de otra entidad) | SEASON-2.08 | `?fields=geometry` | 400 | WebMvc | ❌ |
| `id` con formato incorrecto | SEASON-2.09 | `GET /season/abc` | 400 (binding) | WebMvc | ❌ |
| Encoding UTF-8 en `observations` | SEASON-2.10 | seed con observations UTF-8 | 200; chars correctos en JSON | WebMvc + JDBC | ❌ |

---

## 3. Tests de `GET /season/terrain/{terrainId}` — listar por terreno

| Caso | ID | Request / Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Terreno con varias seasons | SEASON-3.01 | seed 3 seasons del mismo terrain_id | 200; array de 3 ordenado por `start_date DESC` | WebMvc + JDBC | ✅ (`getSeasonsByTerrain_returnsList`, parcial — solo length) |
| Terreno sin seasons | SEASON-3.02 | `terrainId` aleatorio | 200; `[]` | WebMvc | ❌ |
| Orden ascendente vs descendente | SEASON-3.03 | seed con start_date = 2024-01, 2025-01, 2023-01 | 200; orden 2025-01, 2024-01, 2023-01 | JDBC | ❌ |
| `fields=id` | SEASON-3.04 | seed 3; con proyección | 200; cada elemento solo con `id` | WebMvc + JDBC | ❌ |
| `terrainId` no UUID | SEASON-3.05 | `GET /season/terrain/abc` | 400 (binding) | WebMvc | ❌ |
| Terreno con 1000 seasons | SEASON-3.06 | seed 1000 | 200; respuesta < 2 segundos; ordenadas | JDBC + perf | ❌ |
| Mismo terrain_id pero crop_id distinto | SEASON-3.07 | seed: terrain T con crop A y crop B | 200; ambas seasons aparecen | JDBC | ❌ |
| Endpoint NO valida que terrain exista | SEASON-3.08 | `terrainId` desconocido en `terrain-service` | 200; `[]` (no 404). **Documentado como decisión actual** | WebMvc | ❌ |

---

## 4. Tests de `DELETE /season/{id}` — borrado manual

| Caso | ID | Request / Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path | SEASON-4.01 | seed 1 fila; DELETE | 204 sin body; fila desaparece | WebMvc + JDBC | ✅ (`deleteSeason_returns204NoBody`, falta verificación BBDD) |
| `id` inexistente | SEASON-4.02 | UUID aleatorio | 404; `title:"Resource not found"` | WebMvc | ✅ (`deleteSeason_unknownId_returns404`) |
| `id` no UUID | SEASON-4.03 | `DELETE /season/abc` | 400 (binding) | WebMvc | ❌ |
| Doble DELETE | SEASON-4.04 | DELETE OK + DELETE | 204 + 404 | WebMvc + JDBC | ❌ |
| DELETE concurrente | SEASON-4.05 | dos threads sobre el mismo id | uno 204, otro 404 | JDBC | ❌ |
| El DELETE NO publica evento Kafka | SEASON-4.06 | spy sobre `KafkaTemplate` (que no existe en este servicio) | sin invocaciones | unit | ❌ |
| El DELETE NO afecta a otras seasons del mismo terreno | SEASON-4.07 | seed 3 seasons del mismo terrain_id; DELETE 1 | otras 2 permanecen | JDBC | ❌ |

---

## 5. Tests de los gRPC clients

> **Capa:** unit. El stub blocking se mockea con Mockito. No requiere servidor real.

### 5.1 `TerrainGrpcClient`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `checkTerrainExists` happy path | SEASON-5.01 | stub responde `exists=true` | método devuelve `true` | ❌ |
| `checkTerrainExists` exists=false | SEASON-5.02 | stub responde `exists=false` | método devuelve `false` | ❌ |
| `checkTerrainExists` UNAVAILABLE | SEASON-5.03 | stub lanza `StatusRuntimeException(UNAVAILABLE)` | envuelve en `RuntimeException("Failed to verify terrain existence")` | ❌ |
| `checkTerrainExists` con UUID nulo | SEASON-5.04 | input null | hoy lanza NPE en `terrainId.toString()` — **decidir** si validar antes o dejar | ❌ |
| Latencia: timeout configurable | SEASON-5.05 | stub bloquea > deadline | si se añade `deadline`, debería timeout; hoy bloquea ad infinitum | 🟡 |

### 5.2 `CropGrpcClient`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `checkCropExists` happy path | SEASON-5.06 | stub responde `exists=true` | método devuelve `true` | ❌ |
| `checkCropExists` exists=false | SEASON-5.07 | stub responde `exists=false` | método devuelve `false` | ❌ |
| `checkCropExists` UNAVAILABLE | SEASON-5.08 | stub lanza `StatusRuntimeException(UNAVAILABLE)` | envuelve en `RuntimeException("Failed to verify crop existence")` | ❌ |

### 5.3 Contract tests (snapshot del proto)

| Caso | ID | Verificación | Capa | Estado |
|---|---|---|---|---|
| `terrain.proto` aún coincide con el de `terrain-service` | SEASON-5.10 | hash del archivo o golden snapshot | contract | ❌ |
| `crop.proto` aún coincide con el de `crop-service` | SEASON-5.11 | hash del archivo o golden snapshot | contract | ❌ |
| Stubs generados compilan | SEASON-5.12 | `./mvnw -pl season-service compile` | smoke | ✅ (verificado en cada build) |

---

## 6. Tests del listener Kafka `terrain-deleted`

### 6.1 Unit del listener

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `handleTerrainDeleted` delega al service | SEASON-6.01 | mock service; invocar `listener.handleTerrainDeleted(event)` | `seasonService.deleteSeasonsByTerrainId(terrainId)` invocado 1 vez | ✅ (`TerrainDeletedListenerTest`) |
| Evento con `terrainId` nulo | SEASON-6.02 | `new TerrainDeletedEvent(null)` | hoy delega con null → SQL probablemente borra 0 filas; documentar como tolerado | ❌ |
| Excepción en el service | SEASON-6.03 | mock service lanza `RuntimeException` | listener propaga → Spring Kafka logueará y reintenta según `ack-mode` | ❌ |

### 6.2 Integración con `@EmbeddedKafka`

> Capa más realista: arranca un broker en memoria, publica un mensaje y verifica que el listener lo procesa.

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Mensaje publicado con `__TypeId__` correcto | SEASON-6.10 | publicar JSON `{"terrainId":"..."}` con header `__TypeId__=com.agro.terrainservice.event.TerrainDeletedEvent` | `seasonService.deleteSeasonsByTerrainId` invocado | ❌ |
| Mensaje sin header `__TypeId__` | SEASON-6.11 | publicar sin header | con `use.type.headers=true` y `type.mapping`, **falla**. Documentar y decidir si añadir fallback. | ❌ |
| Mensaje malformado (JSON inválido) | SEASON-6.12 | bytes basura | `ErrorHandlingDeserializer` lo aísla; el listener no se invoca | ❌ |
| `at-least-once`: el listener falla antes de commit | SEASON-6.13 | service mock lanza excepción; offset NO debe avanzar | tras restart, el mensaje se reprocesa | ❌ |
| Idempotencia del DELETE | SEASON-6.14 | publicar el mismo mensaje 2 veces | la 2ª vez, `DELETE … WHERE terrain_id=?` afecta 0 filas; sin error | JDBC + Kafka | ❌ |
| Group id correcto | SEASON-6.15 | inspect el `KafkaListenerContainer` | `group-id == "season-service-group"` | smoke | ❌ |

---

## 7. Tests del repositorio (BBDD real con Testcontainers)

> **Capa:** `@JdbcTest + @Import(SeasonRepository.class) + @Testcontainers`. Verifica SQL puro, mapeos y constraints.

| Caso | ID | Operación | Resultado | Estado |
|---|---|---|---|---|
| `createSeason` happy path | SEASON-7.01 | `request` con todos los campos | devuelve UUID generado; fila persistida | ❌ |
| `createSeason` con `end_date NULL` | SEASON-7.02 | end_date null | persiste con NULL en BBDD | ❌ |
| `createSeason` con `season_type_id` inexistente | SEASON-7.03 | `season_type_id=99` | lanza `DataIntegrityViolationException` (FK SQL) | ❌ |
| Constraint `start_before_end` | SEASON-7.04 | end_date < start_date | lanza `DataIntegrityViolationException` | ❌ |
| Constraint `start_date NOT NULL` | SEASON-7.05 | INSERT directo con `start_date NULL` | lanza error PostgreSQL | ❌ |
| `getSeason` existente | SEASON-7.06 | seed; `getSeason(id, "*")` | devuelve mapa con todas las columnas | ❌ |
| `getSeason` inexistente | SEASON-7.07 | UUID aleatorio | lanza `ResourceNotFoundException` con clave `season.not.found` | ❌ |
| `getSeasonsByTerrain` orden | SEASON-7.08 | seed 3 con start_date variadas | resultado ordenado `DESC` | ❌ |
| `getSeasonsByTerrain` vacío | SEASON-7.09 | terrainId sin filas | devuelve `[]` (no excepción) | ❌ |
| `deleteSeason` happy path | SEASON-7.10 | seed; deleteSeason(id) | `rows == 1`; fila eliminada | ❌ |
| `deleteSeason` inexistente | SEASON-7.11 | UUID aleatorio | lanza `ResourceNotFoundException` | ❌ |
| `deleteByTerrainId` con N filas | SEASON-7.12 | seed 3 del mismo terrain_id | borra las 3; las de otros terrenos intactas | ❌ |
| `deleteByTerrainId` sin coincidencias | SEASON-7.13 | terrainId sin filas | sin error; rowcount=0 | ❌ |
| Default UUID generado por Postgres | SEASON-7.14 | INSERT sin id | la fila trae un UUID v4 aleatorio | ❌ |
| Caracteres especiales en `observations` | SEASON-7.15 | `"O'Higgins'", "naïve"` | inserción y lectura sin escapes manuales | ❌ |

### 7.A Configuración Testcontainers

```java
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14"));
    }
}
```

Flyway debe aplicar `V1` automáticamente. Validar: `SELECT COUNT(*) FROM flyway_schema_history >= 1` y `SELECT COUNT(*) FROM season_type == 4`.

---

## 8. Tests de integración cross-service

> Combinan `@SpringBootTest` con WireMock para el gRPC y/o `@EmbeddedKafka` para los eventos.

### 8.1 POST /season → cascada gRPC

| Caso | ID | Mock terrain | Mock crop | Resultado | Estado |
|---|---|---|---|---|---|
| Ambos `exists=true` | SEASON-8.01 | true | true | 201 + INSERT | ❌ |
| terrain `exists=false` | SEASON-8.02 | false | true | 404 `Terrain not found` + sin INSERT + sin llamada a crop | ❌ |
| terrain `exists=true`, crop `exists=false` | SEASON-8.03 | true | false | 404 `Crop not found` + sin INSERT | ❌ |
| terrain UNAVAILABLE | SEASON-8.04 | error gRPC | n/a | 500 + sin INSERT + sin llamada a crop | ❌ |
| crop UNAVAILABLE | SEASON-8.05 | true | error gRPC | 500 + sin INSERT | ❌ |

### 8.2 Cascada Kafka end-to-end

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Recepción de `terrain-deleted` borra todas las seasons del terreno | SEASON-8.10 | seed 5 seasons del mismo terrain_id; publish event | tras 1 s, 0 seasons del terrain | `@EmbeddedKafka` + JDBC | ❌ |
| Recepción no afecta a seasons de otros terrenos | SEASON-8.11 | seed 5 del terrain A + 3 del terrain B; publish para A | A → 0; B → 3 intactas | `@EmbeddedKafka` + JDBC | ❌ |
| Múltiples eventos consecutivos | SEASON-8.12 | publish 10 eventos para 10 terrains distintos | todos procesados; orden de offset respetado | Kafka + JDBC | ❌ |

### 8.3 Smoke test del stack completo

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Levantar contenedores reales (`terrain-service`, `crop-service`, `season-service`, sus BBDDs, Kafka) | SEASON-8.20 | docker compose up | curl POST/GET/DELETE funcionan; flujos end-to-end OK | manual | ❌ |

---

## 9. Tests transversales (i18n, ProblemDetail, headers, locales)

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| Locale español default | SEASON-9.01 | sin `Accept-Language` | mensajes en `es` | ❌ |
| Locale inglés | SEASON-9.02 | `Accept-Language: en` | mensajes en `en` | ❌ |
| Locale desconocido | SEASON-9.03 | `Accept-Language: zh` | fallback a EN | ❌ |
| Multi-locale | SEASON-9.04 | `Accept-Language: zh, es;q=0.5` | resolverá a EN o ES — aceptar cualquier traducción real | ❌ |
| `ProblemDetail` content-type | SEASON-9.05 | cualquier 4xx | `Content-Type: application/problem+json` | ❌ |
| `ProblemDetail` campos mínimos | SEASON-9.06 | cualquier 4xx | contiene `type, title, status, detail` | ❌ |
| `errors[]` en validation 400 | SEASON-9.07 | request inválido | `errors` array con `<campo>: <mensaje>` | ✅ (parcial — `createSeason_invalidBody`) |
| Charset UTF-8 explícito | SEASON-9.08 | response | `Content-Type: application/json;charset=UTF-8` | ❌ |
| Mensaje de validación interpolado con argumentos | SEASON-9.09 | terrain_id inexistente | el `{0}` del mensaje i18n se reemplaza por el UUID | ❌ |

---

## 10. Tests de seguridad (defensivos)

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| SQL injection vía `fields` | SEASON-10.01 | `?fields=id;DROP TABLE season;--` | 400 (binding rechaza); tabla intacta | ❌ |
| SQL injection vía `terrainId` path | SEASON-10.02 | `GET /season/terrain/abc OR 1=1` | 400 (binding) | ❌ |
| Body con propiedades extra (Jackson) | SEASON-10.03 | añade `"isAdmin":true` al `SeasonRequest` | 201 (Jackson ignora por default); no se persiste nada raro | ❌ |
| Body con tipos inesperados | SEASON-10.04 | `"start_date": 12345` | 400 deserialización | ❌ |
| Payload gigante | SEASON-10.05 | body con observations de 5 MB | 413 o 400 (depende del límite Spring) | ❌ |
| Path traversal en `id` | SEASON-10.06 | `DELETE /season/../../etc/passwd` | 400 binding (no UUID) | ❌ |
| Header injection | SEASON-10.07 | `Accept-Language: es\r\nX-Injected: 1` | rechazado por servlet container | ❌ |
| Auth bypass al servicio interno | SEASON-10.08 | request directo a `:8082/season` sin JWT | hoy 200 (el servicio NO valida; el gateway lo hace). Documentado como diseño. | ❌ |
| Rate-limit | SEASON-10.09 | 1000 POST en 1 s | sin rate-limit hoy; mejora futura | 🟡 |

---

## 11. Tests del bloque "ownership" (NUEVO — bloque 🚧)

> **Toda esta sección depende de implementar primero la validación de propiedad** descrita en `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` §4. El objetivo: todo endpoint de `season` debe verificar que el terreno referenciado pertenece al solicitante (vía nuevo RPC `CheckTerrainOwnership` y propagación `X-User-Id` desde el gateway).

### 11.1 Pre-requisito de implementación

- `terrain-service` expone RPC `CheckTerrainOwnership(terrain_id, user_id) → {exists, owned_by_user}` (ver investigación §4.1).
- `api-gateway` decodifica el JWT y propaga `X-User-Id` al downstream (investigación §4.2).
- `season-service` recibe `@RequestHeader("X-User-Id") UUID userId` en cada controller method.

### 11.2 Casos del bloque 🚧

| Caso | ID | Pre / Request | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| `POST /season` sin `X-User-Id` | SEASON-11.01 | cabecera ausente | 400 (`@RequestHeader` obligatorio) | WebMvc | 🚧 |
| `POST /season` con terrain de otro usuario | SEASON-11.02 | mock `CheckTerrainOwnership → exists=true, owned=false` | 403 + `title:"Terrain not owned"` + sin INSERT | WebMvc + integración | 🚧 |
| `POST /season` con terrain inexistente | SEASON-11.03 | mock → `exists=false` | 404 `Terrain not found` | WebMvc | 🚧 |
| `POST /season` con terrain propio | SEASON-11.04 | mock → `exists=true, owned=true` | 201 (continúa con el chequeo de crop) | WebMvc | 🚧 |
| `GET /season/{id}` de otro usuario | SEASON-11.05 | la season existe pero su terrain pertenece a B | 404 (mejor que 403 — no filtrar existencia) | WebMvc | 🚧 |
| `GET /season/terrain/{tid}` de terrain ajeno | SEASON-11.06 | tid no pertenece a userId | 404 | WebMvc | 🚧 |
| `DELETE /season/{id}` de otro usuario | SEASON-11.07 | igual que 11.05 | 404 | WebMvc | 🚧 |
| Race condition: terrain transferido entre check y INSERT | SEASON-11.08 | TOCTOU teórico | la season queda con terrain_id que ya no pertenece al user; será limpiada por el listener Kafka cuando el dueño anterior borre el terrain. Documentar. | integración | 🚧 |
| RPC `CheckTerrainOwnership` schema | SEASON-11.09 | snapshot del `.proto` | contract test | contract | 🚧 |
| i18n: `terrain.not.owned` en ES y EN | SEASON-11.10 | request con `Accept-Language` cambiado | mensajes correctos | WebMvc | 🚧 |

### 11.3 Mapeo del bloque al código

```
season-service/src/main/proto/terrain.proto                 (modify: añadir CheckTerrainOwnership)
season-service/src/main/java/.../grpc/TerrainGrpcClient.java (modify: nuevo método checkTerrainOwnership)
season-service/src/main/java/.../service/SeasonService.java  (modify: createSeason recibe userId, llama al nuevo RPC)
season-service/src/main/java/.../controller/SeasonController.java (modify: @RequestHeader X-User-Id en los 4 endpoints)
season-service/src/main/java/.../exception/ForbiddenException.java (new)
season-service/src/main/java/.../exception/GlobalExceptionHandler.java (modify: handler 403)
season-service/src/main/resources/i18n/messages*.properties (modify: añadir terrain.not.owned)
```

---

## Apéndice A — Matriz de cobertura por capa

| Capa | Casos previstos | Casos hoy verdes | Bloque que los implementa |
|---|---|---|---|
| Unit (Mockito) | ~25 | 8 | §1 (varios), §2.01, §3.01, §4.01-02 |
| WebMvc slice | ~50 | 9 | §1-§4 |
| JDBC (Testcontainers) | ~25 | 0 | §7 entero, §1.A |
| gRPC client unit | ~10 | 0 | §5 entero |
| Kafka listener (unit) | ~3 | 1 | §6.01 |
| Kafka listener (`@EmbeddedKafka`) | ~6 | 0 | §6.10-6.15 |
| Integración cross-service | ~12 | 0 | §8 entero |
| Contract / WireMock | ~5 | 0 | §5.10-5.12, §8 |
| Transversales (i18n / ProblemDetail) | ~9 | 1 | §9 |
| Seguridad | ~9 | 0 | §10 |
| Ownership (bloque 🚧) | ~10 | 0 | §11 — depende de implementar |
| **Total** | **~165** | **19** | |

> Objetivo de cobertura JaCoCo tras implementar todo: ≥ **70 %** en `service/` y `controller/`, ≥ **60 %** global.

---

## Apéndice B — Fixtures listas para copiar

### B.1 `SeasonRequest` válido (mínimo)

```json
{
  "terrain_id": "11111111-1111-1111-1111-111111111111",
  "crop_id": "22222222-2222-2222-2222-222222222222",
  "start_date": "2025-03-01"
}
```

### B.2 `SeasonRequest` válido (completo)

```json
{
  "terrain_id": "11111111-1111-1111-1111-111111111111",
  "crop_id": "22222222-2222-2222-2222-222222222222",
  "start_date": "2025-03-01",
  "end_date": "2025-08-01",
  "season_type_id": 1,
  "observations": "Siembra de trigo blando — campaña 2025"
}
```

### B.3 `SeasonRequest` inválido (3 errores a la vez)

```json
{
  "end_date": "2025-08-01"
}
```

(Falta `terrain_id`, `crop_id`, `start_date`.)

### B.4 SQL seeds para tests JDBC

```sql
-- Truncar season; los seeds de season_type vienen del V1 y deben mantenerse
TRUNCATE TABLE season;

INSERT INTO season (id, terrain_id, crop_id, start_date, end_date, season_type_id, observations) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   '11111111-1111-1111-1111-111111111111',
   '22222222-2222-2222-2222-222222222222',
   '2025-03-01', '2025-08-01', 1, 'Trigo blando 2025'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   '11111111-1111-1111-1111-111111111111',
   '33333333-3333-3333-3333-333333333333',
   '2024-03-01', '2024-08-01', 1, 'Trigo blando 2024'),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc',
   '99999999-9999-9999-9999-999999999999',
   '22222222-2222-2222-2222-222222222222',
   '2025-04-01', NULL, 2, 'Cosecha en curso');
```

### B.5 Mock de gRPC con WireMock (para tests cross-service)

```java
// terrain CheckTerrainExists → exists=true
stubFor(post(urlEqualTo("/com.agro.terrain.grpc.TerrainService/CheckTerrainExists"))
    .willReturn(aResponse()
        .withHeader("Content-Type", "application/grpc")
        .withBody(/* protobuf bytes con exists=true */)));
```

> Para gRPC con WireMock se necesita `wiremock-grpc-extension`. Alternativa más simple: arrancar `terrain-service` real con Testcontainers o usar `InProcessServer` de gRPC.

### B.6 Evento Kafka de prueba

```bash
# Publicar terrain-deleted manualmente con kcat
echo '{"terrainId":"11111111-1111-1111-1111-111111111111"}' | \
  kcat -b localhost:9092 -t terrain-deleted -P \
       -H "__TypeId__=com.agro.terrainservice.event.TerrainDeletedEvent"
```

### B.7 Helper Spring Kafka para tests `@EmbeddedKafka`

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"terrain-deleted"})
class TerrainDeletedListenerKafkaIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void e2e() throws Exception {
        UUID terrainId = UUID.randomUUID();
        // ... seed 3 seasons en BBDD con ese terrain_id ...
        kafkaTemplate.send(MessageBuilder
            .withPayload(new TerrainDeletedEvent(terrainId))
            .setHeader(KafkaHeaders.TOPIC, "terrain-deleted")
            .setHeader("__TypeId__", "com.agro.terrainservice.event.TerrainDeletedEvent")
            .build());

        // Esperar (Awaitility) hasta que el repo no encuentre seasons con ese terrain_id
        await().atMost(5, SECONDS).until(() ->
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM season WHERE terrain_id = ?",
                Integer.class, terrainId) == 0);
    }
}
```

---

## Apéndice C — Orden de implementación sugerido

> Recomendación para que el desarrollador avance sin bloqueos.

### Fase 1 — completar coverage del estado actual (sin nueva funcionalidad)

1. Implementar §1 entero (`SeasonControllerTest` extendido). Un commit.
2. Implementar §2 entero (`SeasonControllerDetailTest`). Un commit.
3. Implementar §3 entero (`SeasonControllerListByTerrainTest`). Un commit.
4. Implementar §4 entero (`SeasonControllerDeleteTest`). Un commit.
5. Implementar §5 (gRPC clients unit). Un commit.
6. Implementar §6.01-6.03 (listener unit). Un commit (extender el existente `TerrainDeletedListenerTest`).
7. Implementar §7 entero (Testcontainers `SeasonRepositoryIT`). Un commit. **Requiere Docker**.
8. Implementar §9 (transversales i18n). Un commit.
9. Implementar §10 (seguridad defensiva). Un commit.

**Hito:** todos los tests verdes; cobertura JaCoCo ≥ 60 %.

### Fase 2 — integración

1. Implementar §6.10-6.15 (`@EmbeddedKafka` listener IT). Un commit.
2. Implementar §8 entero (cross-service con WireMock o `InProcessServer` gRPC). Un commit.
3. Documentar §8.20 (smoke manual con `docker compose up`) en un script bash.

**Hito:** flujos end-to-end probados; cobertura ≥ 70 %.

### Fase 3 — bloque 🚧 ownership (cuando se implemente)

1. **Pre-requisito**: completar el Bloque A descrito en `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` §4-§6 (RPC `CheckTerrainOwnership`, `X-User-Id` en gateway, refactor del controller).
2. Implementar §11 entero como TDD-spec.
3. Eliminar la deuda transversal de "el servicio no conoce al usuario".

**Hito:** todos los tests de §11 verdes; el contrato de propiedad está cerrado.

### Fase 4 — script de QA bash (opcional)

Análogo al `terrain-service/test-terrain-plan.sh` y al `crop-service/test-crop-plan.sh`. Cubre los casos verificables desde fuera del JVM (HTTP + Kafka + gRPC) con `curl`, `kcat` y `grpcurl`. Da feedback rápido sin requerir build de Maven.

---

## Notas finales

1. **Cualquier cambio en este plan debe ir acompañado de un commit que actualice el `.md` y los tests asociados** — ambos archivos van juntos.
2. **No mezclar tests de fases distintas en el mismo PR**: los 🚧 deben ir solos para que el revisor pueda razonar sobre el contrato cross-service sin distraerse con el resto.
3. **Si un caso del plan es difícil de cubrir** (concurrencia, tiempos, fallo de infra) → marcarlo como `@Tag("flaky")` o `@Disabled` con justificación, **nunca** silenciar la causa raíz.
4. **La fuente de verdad del comportamiento es el código + este plan + `SEASON-SERVICE-DOCUMENTATION.md`.** Si los tres divergen, abrir issue.
