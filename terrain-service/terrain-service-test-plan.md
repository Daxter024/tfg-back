# `terrain-service` — Plan de tests exhaustivo

> Documento de referencia para que un desarrollador (humano o agente IA) pueda **implementar** y **verificar** todos los casos de comportamiento del microservicio `terrain-service` tras la aplicación del paquete 02 (HU-TER-01, HU-TER-03, HU-TER-05).
>
> Cada caso del plan es **observable desde la API pública** o desde el side-effect verificable (BBDD, evento Kafka, registro en disco, llamada gRPC). Para cada uno se indica:
> - **Cómo provocarlo** (request HTTP, evento, etc.).
> - **Qué debe devolver el servicio** (status, body, mensaje i18n exacto).
> - **Qué debe quedar persistido o emitido** (cuando aplica).
> - **Capa de test recomendada** (unit / WebMvc / repo / integración).
>
> **Convención de IDs**: cada caso lleva un identificador estable `TER-XX.YY` que el desarrollador puede usar como nombre del método de test (`@DisplayName`).

---

## Índice

- [0. Convenciones y plantilla común](#0-convenciones-y-plantilla-común)
- [1. Tests de `POST /terrain` — alta de terreno](#1-tests-de-post-terrain--alta-de-terreno)
- [2. Tests de `GET /terrain` — listar / detalle](#2-tests-de-get-terrain--listar--detalle)
- [3. Tests de `DELETE /terrain/{id}` — borrado](#3-tests-de-delete-terrainid--borrado)
- [4. Tests de proyección dinámica `fields=`](#4-tests-de-proyección-dinámica-fields)
- [5. Tests de `POST /terrain/{id}/attachment` — subida](#5-tests-de-post-terrainidattachment--subida)
- [6. Tests de `GET /terrain/{id}/attachment` — listado](#6-tests-de-get-terrainidattachment--listado)
- [7. Tests de `GET …/attachment/{id}/content` — descarga](#7-tests-de-get-attachmentidcontent--descarga)
- [8. Tests de `DELETE …/attachment/{id}` — borrado de adjunto](#8-tests-de-delete-attachmentid--borrado-de-adjunto)
- [9. Tests de `POST /terrain/import` — Catastro / SIGPAC](#9-tests-de-post-terrainimport--catastro--sigpac)
- [10. Tests de gRPC `CheckTerrainExists`](#10-tests-de-grpc-checkterrainexists)
- [11. Tests del listener Kafka `user-deleted`](#11-tests-del-listener-kafka-user-deleted)
- [12. Tests del repositorio (BBDD real)](#12-tests-del-repositorio-bbdd-real)
- [13. Tests del gateway (smoke)](#13-tests-del-gateway-smoke)
- [14. Tests transversales (i18n, ProblemDetail, headers)](#14-tests-transversales-i18n-problemdetail-headers)
- [Apéndice A — Matriz cobertura por capa](#apéndice-a--matriz-cobertura-por-capa)
- [Apéndice B — Fixtures GeoJSON listos para copiar](#apéndice-b--fixtures-geojson-listos-para-copiar)

---

## 0. Convenciones y plantilla común

### 0.1 Capas de test

| Capa | Tecnología | Cuándo usarla |
|---|---|---|
| **Unit** | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Lógica del service, ramificaciones de excepciones. Todos los colaboradores mockeados. |
| **WebMvc slice** | `@WebMvcTest(controllers=…) + @MockitoBean` | Verificar status HTTP, mapping JSON, headers. El service va mockeado. |
| **JDBC slice** | `@JdbcTest + @Import(Repository)` con H2 en modo PG | SQL y mapeos sin funciones espaciales. |
| **Integración PostGIS** | `@SpringBootTest + @Testcontainers` con `postgis/postgis:15-3.5` y broker Kafka | Geometría real + Flyway + cascade + listeners Kafka. **Pendiente** en el repo, pero el plan lo prevé. |

### 0.2 Plantilla de aserciones para errores

Toda respuesta de error debe ser `application/problem+json` (RFC 7807). Verificar:

```java
mockMvc.perform(...)
    .andExpect(status().is(<status>))
    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
    .andExpect(jsonPath("$.status").value(<status>))
    .andExpect(jsonPath("$.title").value("<Title del handler>"))
    .andExpect(jsonPath("$.detail").value(containsString("<clave o trozo i18n>")));
```

### 0.3 Aserciones recomendadas para mensajes i18n

- En **unit tests** se mockea `I18nService` para que devuelva la propia clave (`when(i18nService.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0))`) — así el assert se hace contra la clave (`"terrain.created"`), no contra el texto traducido.
- En **WebMvc tests** que prueben rutas de i18n, mandar la cabecera `Accept-Language: es` (default) y `Accept-Language: en` para validar que el `LocaleResolver` cambia el bundle.

### 0.4 Estado inicial supuesto

Antes de cada test deben restablecerse:
1. Tablas vacías (truncar `terrain` y `attachment` con cascade).
2. `${ATTACHMENTS_STORAGE_ROOT}` vacío (carpeta limpia).
3. Mocks de `UserGrpcClient` reseteados.
4. Topics Kafka consumidos limpiados (en tests de integración con Testcontainers Kafka).

---

## 1. Tests de `POST /terrain` — alta de terreno

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Happy path mínimo (solo campos obligatorios) | TER-1.01 | body con `name`, `user_id`, `geometry` válidos; resto en null | `201`; body `{ id, message:"Terreno con nombre <name> creado exitosamente." }`; fila en `terrain` con descriptivos NULL |
| Happy path completo | TER-1.02 | añade `soil_type`, `slope_percent`, `irrigation`, `cadastral_ref` | `201`; valores correctamente persistidos en BBDD; `area_m2`/`perimeter_m`/`centroid` calculados por PostGIS y devueltos en `GET` |
| `name` ausente | TER-1.03 | body sin `name` o `name=""` | `400`; `errors` contiene `"name: terrain.name.required"` |
| `name` > 255 chars | TER-1.04 | `name` con 256 chars | `400`; `errors` contiene `"name: terrain.name.too.long"` |
| `user_id` ausente | TER-1.05 | body sin `user_id` | `400`; `errors` contiene `"user_id: terrain.user_id.required"` |
| `user_id` no UUID | TER-1.06 | `"user_id":"abc"` | `400` deserialización de Jackson; `title:"Wrong payload"` |
| `user_id` inexistente en `auth-service` | TER-1.07 | `UserGrpcClient.validateUser` → false | `404`; `title:"User not found"`; `detail:"El usuario con ID <uuid> no existe."` |
| `geometry` ausente | TER-1.08 | body sin `geometry` o `geometry={}` | `400`; `errors` contiene `"geometry: terrain.geometry.required"` |
| `geometry` no es Polygon válido | TER-1.09 | `geometry` es un string o estructura no JSON serializable | `400`; `title:"Invalid geometry"`; `detail:"error.geojson"` |
| `geometry` con SRID distinto de 4326 | TER-1.10 | GeoJSON con `crs` apuntando a 3857 (PostGIS lo rechaza por `terrain_geom_srid`) | `400`; `title:"Invalid geometry"`; `detail:"terrain.geometry.invalid"` |
| `geometry` no cerrada / inválida | TER-1.11 | polígono con primer ≠ último vértice (PostGIS dispara `terrain_geom_valid`) | `400`; `title:"Invalid geometry"` |
| Área < 100 m² | TER-1.12 | polígono diminuto (~50 m²) | `400`; `title:"Area out of range"`; `detail:"La superficie del terreno debe estar entre 100.0 y 1.0E8 m^2."` |
| Área > 1e8 m² | TER-1.13 | polígono >10 000 ha | `400`; `title:"Area out of range"` |
| `soil_type` no en enum | TER-1.14 | `"soil_type":"plastico"` | `400` deserialización de Jackson |
| `irrigation` no en enum | TER-1.15 | `"irrigation":"manguera"` | `400` deserialización de Jackson |
| `slope_percent` < 0 | TER-1.16 | `slope_percent:-0.1` | `400`; `errors` contiene `"slope_percent: terrain.slope.invalid"` |
| `slope_percent` > 100 | TER-1.17 | `slope_percent:100.01` | `400`; `errors` contiene `"slope_percent: terrain.slope.invalid"` |
| `slope_percent` en frontera (0 y 100) | TER-1.18 | `0.0` y `100.0` | `201` ambos |
| `cadastral_ref` mal formada | TER-1.19 | `"abc"` o `"abcd"` (≤ 13 chars) | `400`; `errors` contiene `"cadastral_ref: terrain.cadastral_ref.malformed"` |
| `cadastral_ref` con minúsculas | TER-1.20 | `"abcd1234efghij"` | `400`; mismo mensaje (la regex exige `[0-9A-Z]`) |
| `cadastral_ref` 14 chars válida | TER-1.21 | `"1234ABCD5678EF"` | `201` |
| `cadastral_ref` 20 chars válida | TER-1.22 | `"9872023VH5797S0001WX"` | `201` |
| `cadastral_ref` 21 chars | TER-1.23 | string de 21 chars | `400` malformada |
| Content-Type no JSON | TER-1.24 | `Content-Type: text/plain` | `415` Unsupported Media Type |
| Body JSON malformado | TER-1.25 | string truncado | `400`; Jackson lanza `HttpMessageNotReadableException` |
| Sin JWT (vía gateway) | TER-1.26 | sin cabecera `Authorization` | `401` (lo emite el gateway, no el microservicio) |
| `auth-service` caído (gRPC) | TER-1.27 | el stub gRPC lanza `StatusRuntimeException` | `500` (o el status que el handler genérico defina; **verificar comportamiento actual y documentarlo**) |
| Idempotencia de error (no se persiste si gRPC dice false) | TER-1.28 | TER-1.07 | la tabla `terrain` no tiene fila nueva (`SELECT count(*) = 0`) |
| Idempotencia de error (no se persiste si serialización falla) | TER-1.29 | TER-1.09 | la tabla `terrain` no tiene fila nueva |
| Mensaje en inglés | TER-1.30 | TER-1.01 con `Accept-Language: en` | `message:"Terrain named <name> created successfully."` |

**Capa recomendada por caso:**
- TER-1.01–1.02 → integración (PostGIS) para que `area_m2` se calcule de verdad.
- TER-1.03–1.06, 1.08, 1.14–1.25, 1.30 → **WebMvc slice**.
- TER-1.07, 1.09, 1.27–1.29 → **unit del service** (mockear `UserGrpcClient`, `ObjectMapper`, `TerrainRepository`).
- TER-1.10–1.13 → integración (PostGIS, las constraints son SQL).

---

## 2. Tests de `GET /terrain` — listar / detalle

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Listar sin terrenos del usuario | TER-2.01 | `GET /terrain?user_id=<uuid>` con tabla vacía | `200`; body `[]` |
| Listar con varios terrenos | TER-2.02 | inserta 3 filas con `user_id=X`, 2 con otro user_id | `200`; lista con 3 elementos, todos con `user_id=X` |
| Listar sin parámetro `user_id` | TER-2.03 | `GET /terrain` sin query | `400`; `MissingServletRequestParameterException` (Spring) → `title:"Wrong payload"` |
| Listar con `user_id` mal formado | TER-2.04 | `?user_id=abc` | `400`; `title:"Illegal argument"` (Spring lanza `MethodArgumentTypeMismatchException`) |
| Detalle existente | TER-2.05 | `GET /terrain/{id}` con id real | `200`; mapa con todas las columnas de `TerrainFields` |
| Detalle inexistente | TER-2.06 | id que no existe | `404`; `title:"Terrain not found"`; `detail:"No se pudo encontrar el terreno con ID: <id>."` |
| Detalle con UUID malformado | TER-2.07 | `GET /terrain/abc` | `400` |
| Detalle: `geometry` viene como GeoJSON string | TER-2.08 | `?fields=geometry` | la columna `geometry` se serializa por `ST_AsGeoJSON`; el JSON es parseable como `Map` |
| Detalle: `centroid` legible | TER-2.09 | `?fields=centroid` | string parseable como GeoJSON Point |
| Detalle: timestamps en ISO-8601 | TER-2.10 | `?fields=created_at,updated_at` | strings ISO-8601 con offset |

**Capa recomendada:**
- TER-2.01–2.04, 2.06–2.07 → WebMvc slice (mockear el service).
- TER-2.05, 2.08–2.10 → integración PostGIS.

---

## 3. Tests de `DELETE /terrain/{id}` — borrado

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Borrado del propietario | TER-3.01 | `DELETE /terrain/{id}?user_id=<owner>` | `204`; fila desaparece de `terrain`; **se publica `terrain-deleted`** con `terrainId` correcto en el topic |
| Borrado por usuario equivocado | TER-3.02 | `DELETE /terrain/{id}?user_id=<otro>` | `404`; `title:"Terrain not found"`; fila **sigue** en BBDD; **no** se publica evento |
| Borrado de id inexistente | TER-3.03 | id aleatorio | `404`; `title:"Terrain not found"` |
| Cascade borra adjuntos | TER-3.04 | terreno con 3 adjuntos previos | tras el `DELETE` la tabla `attachment` no tiene filas con ese `terrain_id` (PostgreSQL `ON DELETE CASCADE`); los binarios en disco **no** se borran automáticamente (deuda conocida — verificar si es deseable o hay que añadir limpieza) |
| Sin `user_id` | TER-3.05 | `DELETE /terrain/{id}` | `400` `MissingServletRequestParameterException` |
| `user_id` mal formado | TER-3.06 | `?user_id=abc` | `400`; `title:"Illegal argument"` |
| Idempotencia (segundo DELETE) | TER-3.07 | TER-3.01 dos veces | primer call `204`, segundo `404` |

**Capa recomendada:** TER-3.01, 3.04 → integración (Kafka + PostGIS); resto → WebMvc slice.

---

## 4. Tests de proyección dinámica `fields=`

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Sin `fields=` | TER-4.01 | `GET /terrain/{id}` | devuelve **todas** las columnas del enum `TerrainFields` |
| `fields` con un solo campo | TER-4.02 | `?fields=name` | mapa con **una** clave `name` |
| `fields` con varios | TER-4.03 | `?fields=id,name,area_m2` | mapa con esas 3 claves |
| `fields=geometry` | TER-4.04 | request | la clave `geometry` viene como GeoJSON (string parseable) |
| `fields=centroid` | TER-4.05 | request | la clave `centroid` viene como GeoJSON Point |
| `fields` con campo desconocido | TER-4.06 | `?fields=password` | `400`; `title:"Invalid field"` |
| `fields` con caso mixto | TER-4.07 | `?fields=NAME` | depende del enum (Java enum es case-sensitive); el comportamiento esperado actual es `400 Invalid field` — **verificarlo y documentarlo**. |
| `fields` vacío | TER-4.08 | `?fields=` | igual que sin `fields=` (lista vacía → `*`) |
| `fields` con duplicados | TER-4.09 | `?fields=id,id,name` | `200`; el SELECT acepta duplicados (idempotente) |
| `fields` con espacios | TER-4.10 | `?fields=id, name` | depende del split; verificar y documentar |
| Inyección SQL vía `fields` | TER-4.11 | `?fields=id;DROP TABLE terrain` | `400 Invalid field`; **no** debe ejecutar nada (la whitelist por enum lo bloquea antes del SELECT) |

**Capa recomendada:** WebMvc slice (mockear repo) para 4.06–4.11; integración para 4.01–4.05.

---

## 5. Tests de `POST /terrain/{id}/attachment` — subida

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Subida JPG válida | TER-5.01 | multipart `file` con `image/jpeg`, 1 KB | `201`; `AttachmentDTO` con `mime_type=image/jpeg`, `size_bytes=1024`, `download_url=/terrain/<tid>/attachment/<id>/content`; fila en `attachment`; binario en `${ATTACHMENTS_STORAGE_ROOT}` |
| Subida PNG válida | TER-5.02 | `image/png`, 2 KB | `201` |
| Subida PDF válida | TER-5.03 | `application/pdf`, 100 KB | `201` |
| MIME no permitido (`text/plain`) | TER-5.04 | `text/plain`, 100 B | `415`; `title:"Attachment MIME type not allowed"`; `detail` contiene `attachment.mime.forbidden` y el MIME real |
| MIME no permitido (`application/zip`) | TER-5.05 | `application/zip` | `415` |
| MIME ausente (Content-Type vacío en el part) | TER-5.06 | request multipart sin `Content-Type` en el part | `415`; `detail` contiene `attachment.mime.forbidden` con `unknown` |
| Tamaño = 0 | TER-5.07 | byte array vacío | `400`; `title:"Attachment quota exceeded"` (la rama del service trata `size <= 0` igual que > MAX) — **verificar comportamiento** y reflejarlo en doc |
| Tamaño 1 byte | TER-5.08 | 1 byte | `201` |
| Tamaño = 10 MB exacto | TER-5.09 | array de `10*1024*1024` bytes | `201` |
| Tamaño = 10 MB + 1 byte | TER-5.10 | array de `10*1024*1024 + 1` | `413`; `title:"Attachment size exceeded"`; `detail:"attachment.size.exceeded"` (lo lanza Spring antes del controller) |
| Tamaño individual OK pero cuota acumulada superada | TER-5.11 | terreno ya tiene 95 MB; nuevo de 10 MB | `400`; `title:"Attachment quota exceeded"`; `detail:"attachment.quota.exceeded"` con `terrain_id` |
| Cuota justo en el límite (suma=100 MB) | TER-5.12 | terreno ya tiene 90 MB, nuevo de 10 MB | `201` |
| Terreno inexistente | TER-5.13 | `terrainId` aleatorio | `404`; `title:"Terrain not found"` |
| Terreno existe pero pertenece a otro usuario | TER-5.14 | `user_id` ≠ propietario | `404`; `title:"Terrain not found"` (el service usa `existsForUser`) |
| Sin parámetro `user_id` | TER-5.15 | request sin `?user_id=` | `400` |
| Sin parte `file` | TER-5.16 | request multipart sin el part `file` | `400` `MultipartException`; `title:"Multipart request error"` |
| Multiple files (no soportado) | TER-5.17 | dos parts con name=`file` | comportamiento actual: el primero se procesa; **verificar y documentar** si es el comportamiento esperado |
| Storage falla (IO) | TER-5.18 | mockear `FileStorageService.store` para lanzar `IOException` | `500` (RuntimeException genérica) — **considerar añadir handler dedicado** para devolver 503 |
| Idempotencia tras error | TER-5.19 | TER-5.18 | la fila NO se inserta en `attachment` (la transacción debe revertirse) |
| Persistencia tras éxito | TER-5.20 | TER-5.01 + posterior `GET .../{attachmentId}/content` | descarga devuelve los mismos bytes que se subieron |

**Capa recomendada:** WebMvc para 5.04–5.07, 5.13–5.16; unit del service para 5.10–5.12, 5.18–5.19; integración para 5.01–5.03, 5.20.

---

## 6. Tests de `GET /terrain/{id}/attachment` — listado

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Listar terreno sin adjuntos | TER-6.01 | `GET /terrain/{id}/attachment?user_id=<owner>` | `200`; `[]` |
| Listar terreno con N adjuntos | TER-6.02 | terreno con 3 adjuntos | `200`; array de 3 DTOs con `terrain_id` correcto |
| Terreno inexistente | TER-6.03 | id aleatorio | `404`; `title:"Terrain not found"` |
| Terreno de otro usuario | TER-6.04 | `user_id` ≠ propietario | `404` |
| Sin `user_id` | TER-6.05 | sin query | `400` |
| `download_url` correcta | TER-6.06 | TER-6.02 | cada DTO trae `download_url=/terrain/<tid>/attachment/<id>/content` |

**Capa recomendada:** WebMvc + unit; integración para 6.06.

---

## 7. Tests de `GET …/attachment/{id}/content` — descarga

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Descarga JPG existente | TER-7.01 | tras subir TER-5.01 | `200`; `Content-Type:image/jpeg`; `Content-Length` = `size_bytes`; `Content-Disposition: inline; filename="<original_name>"`; bytes idénticos al original |
| Descarga PNG | TER-7.02 | tras subir PNG | `Content-Type:image/png` |
| Descarga PDF | TER-7.03 | tras subir PDF | `Content-Type:application/pdf` |
| ID inexistente | TER-7.04 | UUID aleatorio | `404`; `title:"Attachment not found"`; `detail:"attachment.not.found"` |
| ID de adjunto pertenece a otro terreno | TER-7.05 | mismatch entre `terrainId` en la URL y el terrain_id real del adjunto | `404`; mismo mensaje |
| Storage perdido (fichero borrado del disco) | TER-7.06 | borrar el fichero antes de descargar | `404`; `title:"Attachment not found"` (el service captura `IOException` en `load`) |
| UUID malformado en path | TER-7.07 | `/attachment/xyz/content` | `400`; `title:"Illegal argument"` |
| Sin JWT (vía gateway) | TER-7.08 | sin cabecera | `401` (gateway) |

**Capa recomendada:** integración para 7.01–7.03, 7.06; WebMvc para el resto.

---

## 8. Tests de `DELETE …/attachment/{id}` — borrado de adjunto

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Borrado del propietario | TER-8.01 | `DELETE` con `user_id` correcto | `204`; fila desaparece; binario borrado del disco |
| ID inexistente | TER-8.02 | UUID aleatorio | `404`; `title:"Attachment not found"` |
| Adjunto de otro terreno (ownership mismatch) | TER-8.03 | terrainId ≠ attachment.terrain_id | `404` |
| Terreno de otro usuario | TER-8.04 | user_id ≠ propietario del terreno | `404`; `title:"Terrain not found"` |
| Sin `user_id` | TER-8.05 | sin query | `400` |
| Idempotencia | TER-8.06 | TER-8.01 dos veces | primer call `204`, segundo `404` |
| Cuota se libera | TER-8.07 | terreno con cuota al límite, borro 1 adjunto, subo otro nuevo | el segundo upload pasa (la suma vuelve a estar bajo 100 MB) |

**Capa recomendada:** integración para 8.01, 8.07; WebMvc para 8.02–8.06.

---

## 9. Tests de `POST /terrain/import` — Catastro / SIGPAC

### 9.1 Validación sintáctica (no llega al proveedor externo)

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| `reference` ausente | TER-9.01 | sin campo `reference` o vacío | `400`; `errors` contiene `cadastral.reference.malformed` |
| `kind` ausente | TER-9.02 | sin `kind` | `400` Jackson |
| `kind` inválido | TER-9.03 | `"kind":"OTRO"` | `400` Jackson |
| Cadastral con 19 chars | TER-9.04 | 19 chars | `400`; `title:"Cadastral import failed"`; `detail:"cadastral.reference.malformed"` |
| Cadastral con 21 chars | TER-9.05 | 21 chars | `400` |
| Cadastral con minúsculas | TER-9.06 | `"abcd…"` | `400` |
| Cadastral con guiones | TER-9.07 | `"1234-ABCD-…"` | `400` |
| SIGPAC sin guiones | TER-9.08 | `"13082010200100212"` | `400` |
| SIGPAC con tramo extra | TER-9.09 | `"13-082-01-02-001-002-1-9"` | `400` |
| SIGPAC válido mínimo | TER-9.10 | `"13-082-1-1-1-1-1"` (los `\d{1,X}` lo permiten) | el formato sintáctico pasa pero el provider no está configurado → `502` |

### 9.2 Comportamiento sin proveedor configurado (estado por defecto)

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| `cadastro.api.base-url` vacía | TER-9.11 | Cadastral válida | `502`; `title:"Cadastral import failed"`; `detail:"cadastral.api.unavailable"`; log `WARN "No base URL configured for cadastro provider"` |
| `sigpac.api.base-url` vacía | TER-9.12 | SIGPAC válida | `502`; mismo detail |

### 9.3 Comportamiento con proveedor configurado (mock con WireMock o `RestClient` mockeado)

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Provider devuelve `200` con payload completo | TER-9.13 | mock devuelve JSON con todas las claves | `200`; body `CadastralImportResponse` con todos los campos mapeados |
| Provider devuelve `200` con campos parciales | TER-9.14 | falta `soil_class`, `province` | `200`; esos campos vienen `null` |
| Provider devuelve `404` | TER-9.15 | mock 404 | `404`; `detail:"cadastral.reference.not.found"` |
| Provider devuelve `400` | TER-9.16 | mock 400 | `502`; `detail:"cadastral.api.unavailable"` |
| Provider devuelve `500` | TER-9.17 | mock 500 | `502`; `detail:"cadastral.api.unavailable"` |
| Provider devuelve `503` | TER-9.18 | mock 503 | `502` |
| Timeout | TER-9.19 | mock con `delay > readTimeout` | `504`; `detail:"cadastral.api.timeout"` |
| Conexión rechazada | TER-9.20 | provider apagado | `504` (`ResourceAccessException`) |
| Provider devuelve area como string numérica | TER-9.21 | `"area_m2":"123.45"` | `200`; `area_m2=123.45` (parseado) |
| Provider devuelve area como string no numérica | TER-9.22 | `"area_m2":"NaN-x"` | `200`; `area_m2=null` |

> **Importante**: este endpoint **no persiste**. Cualquier test que ejecute `POST /import` debe verificar que la tabla `terrain` no tiene filas nuevas tras la llamada (`SELECT count(*) … = constante`).

**Capa recomendada:** unit para 9.01–9.12; integración con WireMock para 9.13–9.22.

---

## 10. Tests de gRPC `CheckTerrainExists`

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| Terreno existente | TER-10.01 | `terrain_id` real | `exists=true` |
| Terreno inexistente | TER-10.02 | UUID aleatorio | `exists=false` |
| UUID malformado | TER-10.03 | `terrain_id="abc"` | `exists=false` (atrapado el `IllegalArgumentException`); **no** lanza error gRPC |
| Cadena vacía | TER-10.04 | `""` | `exists=false` |
| Concurrencia (100 llamadas paralelas) | TER-10.05 | `terrain_id` válido x100 | todas devuelven `exists=true`; sin error |

**Capa recomendada:** integración con `@SpringBootTest` y `InProcessChannelBuilder` o `grpc-server-spring-boot-starter` test utilities.

---

## 11. Tests del listener Kafka `user-deleted`

| Caso | ID | Setup | Resultado esperado |
|---|---|---|---|
| Usuario tiene 0 terrenos | TER-11.01 | tabla vacía | listener no falla; cero eventos `terrain-deleted` emitidos |
| Usuario tiene 1 terreno (sin adjuntos) | TER-11.02 | 1 fila en `terrain` | tras el evento: tabla `terrain` vacía para ese user_id; `terrain-deleted` publicado 1 vez con el `terrainId` correcto |
| Usuario tiene 3 terrenos | TER-11.03 | 3 filas | 3 eventos `terrain-deleted` (uno por terreno); orden no relevante |
| Usuario tiene 1 terreno con 2 adjuntos | TER-11.04 | 1 terreno + 2 adjuntos | tras el evento: ambas tablas (`terrain` y `attachment`) sin filas para ese user_id (el cascade SQL borra los adjuntos) |
| `UserDeletedEvent` con `userId=null` | TER-11.05 | payload con null | listener captura y NO falla en bucle (verificar que `findIdsByUserId(null)` no rompe la conexión Kafka). Comportamiento esperado: log error y `ack` para no rebloquear el partition |
| Idempotencia | TER-11.06 | enviar el mismo `UserDeletedEvent` 2 veces | segunda vez no encuentra terrenos → no publica nada; no excepciones |
| Mapping de tipo cross-paquete | TER-11.07 | productor envía `com.agro.authservice.event.UserDeletedEvent` | el `spring.json.type.mapping` traduce a la clase local; payload deserializado correctamente |
| Payload corrupto | TER-11.08 | mensaje no JSON | `ErrorHandlingDeserializer` lo absorbe; offset avanza; no rompe el listener |

**Capa recomendada:** integración con Testcontainers Kafka.

---

## 12. Tests del repositorio (BBDD real)

### 12.1 Sin PostGIS (H2 en modo PG)

| Caso | ID | Acción | Resultado esperado |
|---|---|---|---|
| `existsById` true | TER-12.01 | tras un INSERT | `true` |
| `existsById` false | TER-12.02 | id aleatorio | `false` |
| `findIdsByUserId` filtra correctamente | TER-12.03 | 2 del user X, 1 de otro | devuelve solo los 2 |
| `deleteById` borra exactamente 1 fila | TER-12.04 | tras INSERT + delete | tabla vacía |
| `deleteTerrain(id, ownerEquivocado)` lanza `TerrainNotFoundException` | TER-12.05 | id existe pero owner mismatch | excepción + filas no afectadas |
| `getTerrain(idInexistente)` lanza `TerrainNotFoundException` | TER-12.06 | id aleatorio | excepción |
| `saveWithCalculations` con `soilType=null` no falla | TER-12.07 | descripción opcional | INSERT OK |

### 12.2 Con PostGIS (Testcontainers — pendiente)

| Caso | ID | Acción | Resultado esperado |
|---|---|---|---|
| `area_m2` se calcula | TER-12.10 | INSERT polígono ~1 ha | `area_m2 ≈ 10000` ± tolerancia |
| `perimeter_m` se calcula | TER-12.11 | INSERT polígono | valor coherente con `ST_Perimeter` |
| `centroid` es Point dentro del polígono | TER-12.12 | INSERT polígono | `ST_Within(centroid, geometry) = true` |
| Trigger `set_updated_at` | TER-12.13 | INSERT + UPDATE | `updated_at IS NOT NULL` y > `created_at` |
| Constraint `terrain_geom_valid` rechaza polígono inválido | TER-12.14 | INSERT polígono auto-intersectado | `DataIntegrityViolationException` |
| Constraint `terrain_geom_srid` rechaza SRID ≠ 4326 | TER-12.15 | INSERT con SRID 3857 | `DataIntegrityViolationException` |
| Constraint `terrain_area_range` rechaza < 100 m² | TER-12.16 | INSERT polígono diminuto | `DataIntegrityViolationException` con cause `terrain_area_range` |
| Constraint `terrain_area_range` rechaza > 1e8 m² | TER-12.17 | INSERT polígono enorme | mismo |
| Cascade de `attachment` | TER-12.18 | DELETE terrain con adjuntos | `attachment` queda sin filas para ese terrain_id |
| Índice GIST funciona | TER-12.19 | EXPLAIN sobre query espacial | usa `terrain_geom_gist_idx` |
| `cadastral_ref` indexada | TER-12.20 | EXPLAIN sobre `WHERE cadastral_ref = ?` | usa `idx_terrain_cadastral_ref` |

**Capa recomendada:** 12.01–12.07 → `@JdbcTest` con H2 (ya parcialmente implementado en `TerrainRepositoryTest`); 12.10–12.20 → `@SpringBootTest` con `postgis/postgis:15-3.5`.

---

## 13. Tests del gateway (smoke)

> Estos tests se ejecutan en el módulo `api-gateway` o como integración cross-módulo, no en `terrain-service`. Se incluyen aquí porque cubren el contrato de seguridad de las rutas `/api/terrain/**`.

| Caso | ID | Request | Resultado esperado |
|---|---|---|---|
| `/api/terrain` sin Authorization | TER-13.01 | `GET /api/terrain` sin cabecera | `401` |
| `/api/terrain` con JWT inválido | TER-13.02 | `Authorization: Bearer xxx` | `401` |
| `/api/terrain` con JWT expirado | TER-13.03 | token expirado | `401` |
| `/api/terrain` con JWT válido | TER-13.04 | token reciente | `200` (lo procesa el microservicio) |
| `/api/terrain/import` con JWT | TER-13.05 | token válido | la petición llega al microservicio |
| Circuit breaker abre tras 5xx | TER-13.06 | `terrain-service` apagado | `503`; body de `/fallback/terrain` |

**Capa recomendada:** integración con `@SpringBootTest` levantando `auth-service` + `terrain-service` + `api-gateway` (o stubs HTTP).

---

## 14. Tests transversales (i18n, ProblemDetail, headers)

| Caso | ID | Acción | Resultado esperado |
|---|---|---|---|
| Default locale = `es` | TER-14.01 | request sin `Accept-Language` | mensajes en español |
| Locale `en` | TER-14.02 | `Accept-Language: en` | mensajes en inglés |
| Locale desconocido | TER-14.03 | `Accept-Language: zh-CN` | fallback al `messages.properties` (en) |
| `ProblemDetail` content-type | TER-14.04 | cualquier 4xx | `Content-Type: application/problem+json` |
| `ProblemDetail` campos | TER-14.05 | TER-1.07 | body con `type`, `title`, `status`, `detail` (RFC 7807) |
| Validation errors aparecen como array | TER-14.06 | TER-1.03 + TER-1.05 | `errors` array con `["name: ...", "user_id: ..."]` |
| Mensaje no expone stack trace | TER-14.07 | error interno | el body NO contiene `stackTrace`, `class`, `cause` |
| Cabecera `Content-Type: application/json` en respuestas exitosas | TER-14.08 | TER-1.01 | header presente |
| Sin claves i18n hardcodeadas en código | TER-14.09 | grep `"\\.notfound\\|\\.created\\|\\.invalid\\|\\.required" src/main/java/**/*.java` | resultado vacío salvo en `i18nService.getMessage(...)` |
| Todas las claves usadas existen en ambos bundles | TER-14.10 | recopilar claves de `getMessage(...)` y comparar con `.properties` | unión = intersección (sin huérfanos ni faltantes) |

**Capa recomendada:** WebMvc + análisis estático (grep + script de Maven o JUnit).

---

## Apéndice A — Matriz cobertura por capa

| Sección | Unit | WebMvc | JDBC slice | Integración PostGIS | Integración Kafka | Integración WireMock |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1. POST /terrain | ✅ | ✅ | — | ✅ | — | — |
| 2. GET /terrain | — | ✅ | — | ✅ | — | — |
| 3. DELETE /terrain | ✅ | ✅ | — | ✅ | ✅ | — |
| 4. fields= | ✅ | ✅ | — | ✅ | — | — |
| 5–8. Attachment | ✅ | ✅ | — | ✅ | — | — |
| 9. /import | ✅ | ✅ | — | — | — | ✅ |
| 10. gRPC | — | — | — | ✅ | — | — |
| 11. Listener Kafka | — | — | — | ✅ | ✅ | — |
| 12. Repository | — | — | ✅ | ✅ | — | — |
| 13. Gateway | — | — | — | ✅ | — | — |
| 14. Transversal | — | ✅ | — | — | — | — |

---

## Apéndice B — Fixtures GeoJSON listos para copiar

### B.1 Polígono válido ~1 ha (≈ 10 000 m²)

```json
{
  "type": "Polygon",
  "coordinates": [[
    [-3.7100, 40.4200],
    [-3.7100, 40.4209],
    [-3.7088, 40.4209],
    [-3.7088, 40.4200],
    [-3.7100, 40.4200]
  ]]
}
```

### B.2 Polígono diminuto (≈ 50 m², debe ser rechazado)

```json
{
  "type": "Polygon",
  "coordinates": [[
    [-3.71000, 40.42000],
    [-3.71000, 40.42005],
    [-3.70999, 40.42005],
    [-3.70999, 40.42000],
    [-3.71000, 40.42000]
  ]]
}
```

### B.3 Polígono enorme (≈ 1.1×10⁸ m², debe ser rechazado)

```json
{
  "type": "Polygon",
  "coordinates": [[
    [-3.0, 40.0],
    [-3.0, 41.0],
    [-2.0, 41.0],
    [-2.0, 40.0],
    [-3.0, 40.0]
  ]]
}
```

### B.4 Polígono no cerrado (último ≠ primero)

```json
{
  "type": "Polygon",
  "coordinates": [[
    [-3.71, 40.42],
    [-3.71, 40.43],
    [-3.70, 40.43],
    [-3.70, 40.42]
  ]]
}
```

### B.5 Polígono auto-intersectado (no válido)

```json
{
  "type": "Polygon",
  "coordinates": [[
    [-3.71, 40.42],
    [-3.70, 40.43],
    [-3.71, 40.43],
    [-3.70, 40.42],
    [-3.71, 40.42]
  ]]
}
```

### B.6 Cadastral references válidas

| Tipo | Ejemplo |
|---|---|
| `CADASTRAL` 14 chars (mínimo DTO) | `1234ABCD5678EF` |
| `CADASTRAL` 20 chars (Catastro real) | `9872023VH5797S0001WX` |
| `SIGPAC` válido | `13-082-01-02-001-002-1` |
| `CADASTRAL` mal formada | `abc` / `abcd1234efghij` (minúsculas) / `1234-ABCD-5678` |
| `SIGPAC` mal formada | `13082010200100212` (sin guiones) |

---

## Cómo ejecutar el plan completo

```bash
# Solo unit + WebMvc (rápido, lo que ya existe + lo nuevo)
./mvnw -pl terrain-service test

# Con cobertura JaCoCo
./mvnw -pl terrain-service verify

# Con Testcontainers PostGIS + Kafka (cuando se integren)
./mvnw -pl terrain-service -Pintegration verify
```

**Objetivo de cobertura sugerido**: línea ≥ 80 %, branch ≥ 70 % en `service/`, `controller/`, `repository/`. Excluir `dto/`, `event/`, `model/`, clases generadas por protobuf y `*Application.java`.

---

## Tabla de mapeo: caso → mensaje i18n esperado en `detail`

| ID | Clave i18n (es) | Clave i18n (en) |
|---|---|---|
| TER-1.03 | `terrain.name.required` | `terrain.name.required` |
| TER-1.04 | `terrain.name.too.long` | `terrain.name.too.long` |
| TER-1.05 | `terrain.user_id.required` | `terrain.user_id.required` |
| TER-1.07 | `user.notfound` | `user.notfound` |
| TER-1.08 | `terrain.geometry.required` | `terrain.geometry.required` |
| TER-1.09 | `error.geojson` | `error.geojson` |
| TER-1.10–1.11 | `terrain.geometry.invalid` | `terrain.geometry.invalid` |
| TER-1.12–1.13 | `terrain.area.out.of.range` | `terrain.area.out.of.range` |
| TER-1.16–1.17 | `terrain.slope.invalid` | `terrain.slope.invalid` |
| TER-1.19–1.20 | `terrain.cadastral_ref.malformed` | `terrain.cadastral_ref.malformed` |
| TER-2.06 | `terrain.notfound` | `terrain.notfound` |
| TER-5.04–5.06 | `attachment.mime.forbidden` | `attachment.mime.forbidden` |
| TER-5.10 | `attachment.size.exceeded` | `attachment.size.exceeded` |
| TER-5.11 | `attachment.quota.exceeded` | `attachment.quota.exceeded` |
| TER-5.13–5.14 | `terrain.notfound` | `terrain.notfound` |
| TER-7.04–7.06 | `attachment.not.found` | `attachment.not.found` |
| TER-9.04–9.10 | `cadastral.reference.malformed` | `cadastral.reference.malformed` |
| TER-9.11–9.12, 9.16–9.18 | `cadastral.api.unavailable` | `cadastral.api.unavailable` |
| TER-9.15 | `cadastral.reference.not.found` | `cadastral.reference.not.found` |
| TER-9.19–9.20 | `cadastral.api.timeout` | `cadastral.api.timeout` |

---

## Estado actual de implementación (al cierre del paquete 02)

| Sección | Casos cubiertos hoy | Pendientes |
|---|---|---|
| 1. POST /terrain | TER-1.01 (TerrainController), 1.03/1.04/1.05/1.08 (parcialmente), 1.07, 1.09, 1.12, 1.10 | TER-1.02 (integración), 1.13–1.30 |
| 2. GET /terrain | TER-2.01, 2.05 (parcial) | 2.02–2.04, 2.06–2.10 |
| 3. DELETE /terrain | TER-3.01 (verifica evento), 3.05 implícito | 3.02–3.04, 3.06–3.07 |
| 4. fields | — | todos |
| 5. POST attachment | TER-5.01, 5.04, 5.10/5.11 (cuota), 5.13/5.14 | resto |
| 6. GET attachment | TER-6.01 | resto |
| 7. GET content | TER-7.04, 7.05 | resto |
| 8. DELETE attachment | TER-8.01, parcial | resto |
| 9. POST /import | TER-9.04/9.05/9.08, 9.11/9.12 | 9.13–9.22 (necesitan WireMock) |
| 10. gRPC | — | todos |
| 11. Listener | — | todos |
| 12. Repository | TER-12.01–12.04 (H2) | 12.05–12.20 |
| 13. Gateway | — | todos |
| 14. Transversal | — | 14.01–14.10 |

**Totales:** ~22/180 casos cubiertos. La mayor parte del trabajo restante es:
1. WebMvc tests para los caminos de validación (1.x, 2.x, 5.x).
2. Integración PostGIS para constraints SQL (1.10–1.13, 12.10–12.20, 7.01–7.03).
3. Integración Kafka para listener (11.x).
4. WireMock para `/import` (9.13–9.22).
5. gRPC integration (10.x).
