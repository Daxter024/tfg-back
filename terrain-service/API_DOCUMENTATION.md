# `terrain-service` — Documentación técnica

Estado: rama `claude/terrain-service-paquete-02-v3` tras revert de HU-TER-04. La única entidad de dominio del servicio es **`terrain`** (terreno = parcela: son lo mismo). HU implementadas: HU-TER-01, HU-TER-03, HU-TER-05.

---

## Índice

1. [Topología y puertos](#1-topología-y-puertos)
2. [Cómo llamar al servicio](#2-cómo-llamar-al-servicio)
3. [Esquema de base de datos](#3-esquema-de-base-de-datos)
4. [Endpoints REST](#4-endpoints-rest)
   - [4.1 Terrain](#41-terrain-terrain)
   - [4.2 Attachment](#42-attachment-terrainterrainidattachment)
   - [4.3 Cadastral / SIGPAC import](#43-cadastral--sigpac-import-terrainimport)
5. [Proyección dinámica `fields=`](#5-proyección-dinámica-fields)
6. [gRPC](#6-grpc)
7. [Kafka — eventos producidos y consumidos](#7-kafka--eventos-producidos-y-consumidos)
8. [Catálogo de errores](#8-catálogo-de-errores)
9. [Flujos de trabajo (extremo a extremo)](#9-flujos-de-trabajo-extremo-a-extremo)
10. [I18n](#10-i18n)
11. [Almacenamiento de adjuntos](#11-almacenamiento-de-adjuntos)
12. [Configuración relevante](#12-configuración-relevante)
13. [Testing y QA](#13-testing-y-qa)

---

## 1. Topología y puertos

| Recurso | Local-dev (default) | Docker-compose raíz |
|---|---|---|
| HTTP (microservicio) | `8083` (`application.properties`) | `8080` (`SERVER_PORT=8080` en el bloque `terrain-service`) |
| HTTP a través del gateway | `9001` (`application.yml` default) | `9000` (`SERVER_PORT=9000` en el bloque `api-gateway`) |
| gRPC server | `9093` | `9093` (no expuesto al host por defecto) |
| gRPC clients | `auth-service:9091` (`ValidateUser`) | `auth-service:9091` |
| Base de datos | `localhost:5432` → `terrain_db` | `terrain-db:5432` (PostgreSQL 15 + PostGIS, contenedor `postgis/postgis:15-3.5`) |
| Kafka bootstrap | `localhost:9092` | `kafka:9092` (variable `KAFKA_BROKER` en compose) |
| Kafka topics consumidos | `user-deleted` | idem |
| Kafka topics producidos | `terrain-deleted` | idem |

> En **docker-compose** el gateway resuelve `TERRAIN_SERVICE_URL=http://terrain-service:8080`, que es el `SERVER_PORT` que el contenedor escucha. Si arrancas terrain-service con `./mvnw spring-boot:run` sin variables, escuchará en `8083`. Para alinear el gateway local con tu microservicio local, exporta `TERRAIN_SERVICE_URL=http://localhost:8083`.

> Tras el revert de HU-TER-04, la ruta `/api/terrain/**` en el gateway sigue protegida con `JwtValidation` (HU-TER-05 sigue requiriéndolo).

---

## 2. Cómo llamar al servicio

### 2.1 Por el `api-gateway` (forma recomendada para clientes externos)

- Base URL en docker-compose: `http://localhost:9000/api/terrain`
- Base URL en local-dev: `http://localhost:9001/api/terrain`
- **Filtro JWT activo**: `Authorization: Bearer <token>` obligatorio. Token obtenido en `POST /auth/login` (auth-service).
- El gateway aplica `StripPrefix=1`, por lo que `/api/terrain/foo` llega al microservicio como `/terrain/foo`.

### 2.2 Directamente al microservicio (entornos `dev` o tests internos)

- Base URL en local-dev: `http://localhost:8083/terrain`
- Base URL en docker-compose: `http://terrain-service:8080/terrain` (red interna) o `http://localhost:8080/terrain` desde el host (puerto publicado por el compose).
- **No** valida JWT en este nivel — el microservicio confía en el gateway. Solo válido para desarrollo o invocación interna entre microservicios.

### 2.3 Convención de path params y query params

- `:terrainId` siempre es **UUID** (lanza 400 si llega malformado).
- `user_id` se pasa **siempre como query param** en los endpoints que lo necesitan. El microservicio lo recibe como dato del cliente; el cruce real con el JWT lo hace el gateway. Si la cabecera `Authorization` apunta a un usuario distinto del `user_id`, el gateway no rechaza la petición pero el microservicio devolverá 404 al validar la pertenencia.
- Cuerpos JSON con `Content-Type: application/json` salvo subida de adjuntos (`multipart/form-data`).

---

## 3. Esquema de base de datos

### 3.1 Tabla `terrain` (V1 + V2 + V3)

| Columna | Tipo | Nullable | Descripción |
|---|---|---|---|
| `id` | `UUID` | NO | PK, default `gen_random_uuid()`. |
| `name` | `VARCHAR(255)` | NO | Nombre legible del terreno. |
| `user_id` | `UUID` | NO | Propietario (FK lógica a `auth.users.id`, **no** declarada en SQL). |
| `geometry` | `geometry(Polygon, 4326)` | NO | Polígono cerrado en SRID 4326 (WGS-84). |
| `area_m2` | `NUMERIC(12,2)` | — | **GENERATED ALWAYS** = `ST_Area(geometry::geography)`. Solo lectura. |
| `perimeter_m` | `NUMERIC(12,2)` | — | **GENERATED ALWAYS** = `ST_Perimeter(geometry::geography)`. Solo lectura. |
| `centroid` | `geometry(Point, 4326)` | — | **GENERATED ALWAYS** = `ST_Centroid(geometry)`. Solo lectura. |
| `created_at` | `TIMESTAMP` | NO | Default `NOW()`. |
| `updated_at` | `TIMESTAMP` | SÍ | Trigger `set_updated_at` lo actualiza en cada `UPDATE`. |
| `soil_type` | `enum soil_type` | SÍ | Valores: `arcilloso`, `franco`, `arenoso`, `calizo`, `organico`, `otro`. |
| `slope_percent` | `NUMERIC(5,2)` | SÍ | 0–100 (`CHECK`). |
| `irrigation` | `enum irrigation_type` | SÍ | Valores: `goteo`, `aspersion`, `gravedad`, `secano`. |
| `cadastral_ref` | `VARCHAR(40)` | SÍ | Referencia catastral o SIGPAC (formato validado en DTO). |

**Constraints:**
- `terrain_geom_valid CHECK (ST_IsValid(geometry))`
- `terrain_geom_srid CHECK (ST_SRID(geometry) = 4326)`
- `terrain_area_range CHECK (area_m2 BETWEEN 100 AND 100000000)` — 0,01 ha a 10 000 ha
- Índice GIST sobre `geometry` y BTREE sobre `cadastral_ref`.

### 3.2 Tabla `attachment` (V4)

| Columna | Tipo | Nullable | Descripción |
|---|---|---|---|
| `id` | `UUID` | NO | PK, `gen_random_uuid()`. |
| `terrain_id` | `UUID` | NO | FK → `terrain(id) ON DELETE CASCADE`. |
| `original_name` | `VARCHAR(255)` | NO | Nombre original del fichero. |
| `mime_type` | `VARCHAR(100)` | NO | Whitelist en service (ver §4.2). |
| `size_bytes` | `BIGINT` | NO | `CHECK (> 0 AND ≤ 10485760)` (10 MB por archivo). |
| `storage_key` | `VARCHAR(512)` | NO | Ruta relativa en el almacenamiento (volumen local — opción A). |
| `uploaded_by` | `UUID` | NO | El `user_id` que hizo la subida. |
| `uploaded_at` | `TIMESTAMP` | NO | Default `NOW()`. |

> Cuota **acumulada** por terreno: 100 MB. Validada en service, no en DDL.

---

## 4. Endpoints REST

Todos los endpoints están bajo `/terrain` (microservicio) o `/api/terrain` (a través del gateway). En los ejemplos uso `http://localhost:9000` como gateway (puerto del docker-compose); en local-dev sustitúyelo por `9001`.

### 4.1 Terrain (`/terrain`)

#### `POST /api/terrain` — Crear terreno

Crea un terreno descriptivo (HU-TER-01) opcionalmente prerrellenado por catastro (HU-TER-05).

**Request body** (`application/json`):

```json
{
  "name": "Olivar Norte",
  "user_id": "0c8d4a6e-1f3a-4f1f-bd3a-aa49d6c34d11",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[-3.71,40.42],[-3.71,40.43],[-3.70,40.43],[-3.70,40.42],[-3.71,40.42]]]
  },
  "soil_type": "franco",
  "slope_percent": 4.5,
  "irrigation": "goteo",
  "cadastral_ref": "9872023VH5797S0001WX"
}
```

| Campo | Obligatorio | Tipo | Validaciones |
|---|---|---|---|
| `name` | **sí** | string | `@NotBlank`, ≤ 255 |
| `user_id` | **sí** | UUID | `@NotNull`; debe existir en `auth-service` (gRPC `ValidateUser`) |
| `geometry` | **sí** | GeoJSON Polygon | `@NotEmpty`; PostGIS valida SRID 4326 y `ST_IsValid` |
| `soil_type` | no | enum | uno de `arcilloso`, `franco`, `arenoso`, `calizo`, `organico`, `otro` |
| `slope_percent` | no | double | `0.0 ≤ x ≤ 100.0` |
| `irrigation` | no | enum | uno de `goteo`, `aspersion`, `gravedad`, `secano` |
| `cadastral_ref` | no | string | regex `^[0-9A-Z]{14,20}$` |

**Respuestas:**
- `201 Created` → `{ "id": "<uuid>", "message": "Terreno con nombre <name> creado exitosamente." }`
- `400` payload inválido / GeoJSON inválido / área fuera de [100 m², 100 000 000 m²] / pendiente fuera de rango / `cadastral_ref` mal formada
- `404` `user_id` no existe en `auth-service`

```bash
curl -X POST http://localhost:9000/api/terrain \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d @body.json
```

#### `GET /api/terrain?user_id=<uuid>&fields=<lista>` — Listar terrenos por usuario

| Param | Obligatorio | Tipo | Notas |
|---|---|---|---|
| `user_id` | **sí** | UUID | filtra a los terrenos del propietario |
| `fields` | no | CSV (enum `TerrainFields`) | proyección dinámica — ver §5 |

**Respuesta `200`**: `List<Map<String,Object>>` (cada elemento con las columnas seleccionadas).

#### `GET /api/terrain/{id}?fields=<lista>` — Detalle de un terreno

- `200` → `Map<String,Object>`
- `404` si no existe.

#### `DELETE /api/terrain/{id}?user_id=<uuid>` — Borrar terreno

Borra el terreno y emite `terrain-deleted`. El `ON DELETE CASCADE` SQL borra también los adjuntos asociados.

- `204 No Content`
- `404` si el `id` o el `user_id` no casan.

### 4.2 Attachment (`/terrain/{terrainId}/attachment`)

#### `POST /api/terrain/{terrainId}/attachment?user_id=<uuid>` — Subir adjunto

Petición `multipart/form-data` con un único *part* `file`.

| Restricción | Valor |
|---|---|
| MIME permitidos | `image/jpeg`, `image/png`, `application/pdf` |
| Tamaño por archivo | ≤ 10 MB (`spring.servlet.multipart.max-file-size=10MB`) |
| Tamaño total por terreno | ≤ 100 MB (validado en service sobre la suma de `size_bytes`) |

```bash
curl -X POST "http://localhost:9000/api/terrain/$TID/attachment?user_id=$UID" \
  -H "Authorization: Bearer $JWT" \
  -F "file=@plano.pdf;type=application/pdf"
```

**Respuestas:**
- `201` → `AttachmentDTO`
- `400` cuota total superada
- `404` el terreno no existe **o** no pertenece al `user_id`
- `413` archivo individual > 10 MB
- `415` MIME fuera de la whitelist

`AttachmentDTO`:
```json
{
  "id": "...",
  "terrain_id": "...",
  "original_name": "plano.pdf",
  "mime_type": "application/pdf",
  "size_bytes": 524288,
  "uploaded_by": "...",
  "uploaded_at": "2026-05-08T09:11:00Z",
  "download_url": "/terrain/<terrainId>/attachment/<id>/content"
}
```

#### `GET /api/terrain/{terrainId}/attachment?user_id=<uuid>` — Listar adjuntos

`200` → `List<AttachmentDTO>`. `404` si terreno/usuario no casan.

#### `GET /api/terrain/{terrainId}/attachment/{attachmentId}/content` — Descargar binario

- `200` con cabeceras `Content-Type` (mime real), `Content-Length`, `Content-Disposition: inline; filename="..."`.
- `404` si el adjunto no existe o no pertenece al terreno.
- ⚠ Este endpoint **no exige `user_id`** porque la URL es la que sirve el dashboard tras un `GET /attachment`. La autorización se delega en el JWT del gateway.

#### `DELETE /api/terrain/{terrainId}/attachment/{attachmentId}?user_id=<uuid>` — Borrar adjunto

`204`. Borra el registro en BBDD y el binario en disco.

### 4.3 Cadastral / SIGPAC import (`/terrain/import`)

```http
POST /api/terrain/import
Content-Type: application/json
Authorization: Bearer <jwt>
```

```json
{ "reference": "9872023VH5797S0001WX", "kind": "CADASTRAL" }
```

| Campo | Obligatorio | Validación sintáctica |
|---|---|---|
| `reference` | **sí** | `@NotBlank` |
| `kind` | **sí** | `CADASTRAL` ó `SIGPAC` |

Formatos chequeados antes de salir a la red:
- `CADASTRAL`: `^[0-9A-Z]{20}$`
- `SIGPAC`: `^\d{2}-\d{3}-\d{1,2}-\d{1,2}-\d{1,3}-\d{1,3}-\d+$`

**Respuesta `200`** (`CadastralImportResponse`):
```json
{
  "reference": "9872023VH5797S0001WX",
  "suggested_name": "Polígono 12 Parcela 3",
  "geometry": { "type": "Polygon", "coordinates": [[...]] },
  "area_m2": 32450.12,
  "soil_use": "OLIVAR",
  "soil_class": "II",
  "municipality": "Talavera de la Reina",
  "province": "Toledo"
}
```

> Este endpoint **no crea el terreno**: solo devuelve una propuesta editable. El cliente decide qué campos aceptar y luego invoca `POST /api/terrain` con la `cadastral_ref` rellena.

**Errores:**
- `400` referencia mal formada
- `404` la API externa no encuentra la referencia
- `502` API externa caída o **no configurada** (`cadastro.api.base-url` / `sigpac.api.base-url` vacíos → fallback manual)
- `504` timeout

---

## 5. Proyección dinámica `fields=`

`terrain` acepta `fields=col1,col2,...`. Cada nombre se valida contra el enum `TerrainFields` y se interpola en el `SELECT`. Cualquier campo desconocido devuelve `400 Invalid field`.

`TerrainFields`: `id, name, user_id, geometry, area_m2, perimeter_m, centroid, soil_type, slope_percent, irrigation, cadastral_ref, created_at, updated_at`.

Casos especiales:
- `geometry` en el `SELECT` se traduce a `ST_AsGeoJSON(geometry)` para devolverla legible en JSON.
- Sin `fields=`, el repositorio aplica el equivalente a `SELECT *` (todas las columnas del enum).

---

## 6. gRPC

Servidor gRPC en `:9093`. Plaintext (sin TLS).

### 6.1 `TerrainService.CheckTerrainExists`

```proto
rpc CheckTerrainExists (TerrainIdRequest) returns (TerrainExistsResponse);
message TerrainIdRequest    { string terrain_id = 1; }
message TerrainExistsResponse { bool exists = 1; }
```

Lo consume `season-service` para validar el `terrain_id` antes de insertar una temporada. UUID malformado ⇒ `exists=false`.

### 6.2 Cliente: `auth-service.UserValidationService.ValidateUser`

Se invoca antes de cada `POST /terrain` para confirmar que el `user_id` existe.

---

## 7. Kafka — eventos producidos y consumidos

### 7.1 Producidos

| Topic | Cuándo | Payload | Consumidores conocidos |
|---|---|---|---|
| `terrain-deleted` | Al borrar un terreno (manual o saga RGPD) | `TerrainDeletedEvent { UUID terrainId }` | `season-service` (borra temporadas) |

### 7.2 Consumidos

| Topic | Productor | Acción local |
|---|---|---|
| `user-deleted` | `auth-service` | `TerrainService.deleteTerrainsByUserId(userId)` → para cada terreno del usuario ejecuta el `DELETE` (que también vacía `attachment` vía cascade) → emite `terrain-deleted` por cada uno. |

`spring.json.type.mapping`:
```
com.agro.authservice.event.UserDeletedEvent:com.agro.terrainservice.event.UserDeletedEvent
```

`group-id`: `terrain-service-group`. `auto-offset-reset=earliest`.

---

## 8. Catálogo de errores

Todos los errores devuelven `application/problem+json` (`ProblemDetail` RFC 7807) con `status`, `title` y `detail` (texto i18n).

| Excepción Java | Código HTTP | Cuándo |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Body con `@Valid` falla |
| `InvalidGeometryException` | 400 | GeoJSON ilegible / SRID incorrecto / `ST_IsValid` false |
| `AreaOutOfRangeException` | 400 | `area_m2` < 100 o > 1e8 |
| `InvalidFieldException` | 400 | `fields=` con valor fuera del enum |
| `AttachmentQuotaExceededException` | 400 | suma de adjuntos del terreno > 100 MB |
| `IllegalArgumentException` | 400 | UUID mal formado, etc. |
| `DataIntegrityViolationException` | 400 | constraint genérica que no se mapeó antes |
| `MultipartException` | 400 | request multipart corrupto |
| `TerrainNotFoundException` | 404 | terreno inexistente o no pertenece al `user_id` |
| `UserNotFoundException` | 404 | `auth-service` dice que el `user_id` no existe |
| `AttachmentNotFoundException` | 404 | adjunto inexistente o no pertenece al terreno |
| `MaxUploadSizeExceededException` | 413 | archivo > 10 MB |
| `AttachmentMimeForbiddenException` | 415 | MIME fuera de la whitelist |
| `CadastralImportException` | dinámico (400 / 404 / 502 / 504) | Endpoint `/import`: ver §4.3 |

---

## 9. Flujos de trabajo (extremo a extremo)

### 9.1 Alta de un terreno con adjuntos

```
1. Cliente → POST /auth/login                             → JWT
2. (Opc.) Cliente → POST /api/terrain/import              → propuesta (no persiste)
3. Cliente → POST /api/terrain  (con o sin cadastral_ref) → 201 {id}
   - terrain-service llama gRPC ValidateUser → auth-service:9091
   - PostGIS valida geometría + área
4. Cliente → POST /api/terrain/{id}/attachment            → 201
   - service valida MIME + tamaño + cuota
   - binario va al volumen ATTACHMENTS_STORAGE_ROOT, fila a `attachment`
```

### 9.2 Saga de borrado en cascada (RGPD: el usuario se da de baja)

```
auth-service: DELETE /users/{userId} (admin)
       │ commit BBDD auth
       ▼ Kafka: user-deleted
terrain-service.UserDeletedListener
       │ TerrainService.deleteTerrainsByUserId(userId)
       │   por cada terreno t:
       │     1. DELETE FROM terrain WHERE id=t.id          → cascade en attachment
       │     2. publish terrain-deleted(t.id)
season-service.TerrainDeletedListener
       │ borra todas las temporadas con terrain_id=t.id
```

### 9.3 Borrado manual de un terreno

```
DELETE /api/terrain/{id}?user_id=...
  → DELETE row (cascade SQL → attachment)
  → EventPublisher.publishTerrainDeleted(id)
  ← 204
```

### 9.4 Importación catastral con fallback

```
POST /api/terrain/import {reference, kind}
   ├─ regex falla                  → 400 cadastral.reference.malformed
   ├─ base-url no configurada      → 502 cadastral.api.unavailable (cliente puede ofrecer alta manual)
   ├─ provider 404                 → 404 cadastral.reference.not.found
   ├─ provider 5xx                 → 502 cadastral.api.unavailable
   ├─ timeout / red                → 504 cadastral.api.timeout
   └─ 200 OK                       → propuesta editable

Después: POST /api/terrain con los campos confirmados (debe incluir cadastral_ref).
```

### 9.5 Subida de un adjunto > 10 MB

```
POST /api/terrain/{tid}/attachment
   Spring multipart corta antes de llegar al controller
   → MaxUploadSizeExceededException
   → 413 + i18n attachment.size.exceeded
```

---

## 10. I18n

- Default locale: `es`. Fallback: `en` (`messages.properties`).
- Cabecera `Accept-Language: en` en cualquier petición → respuestas en inglés.
- Bundles completos en:
  - `src/main/resources/i18n/messages_es.properties` (default, español)
  - `src/main/resources/i18n/messages.properties` (fallback, inglés)
- Las claves se agrupan por bloques: `terrain.*`, `attachment.*`, `cadastral.*`, `user.*`, `error.*`.

Muestra de claves más relevantes (ver el bundle para la lista completa):

| Clave | Texto (es) |
|---|---|
| `terrain.created` | `Terreno con nombre {0} creado exitosamente.` |
| `terrain.notfound` | `No se pudo encontrar el terreno con ID: {0}.` |
| `terrain.area.out.of.range` | `La superficie del terreno debe estar entre {0} y {1} m^2.` |
| `terrain.cadastral_ref.malformed` | `La referencia catastral debe tener entre 14 y 20 caracteres alfanuméricos…` |
| `attachment.mime.forbidden` | `Tipo de adjunto {0} no permitido. Tipos válidos: image/jpeg, image/png, application/pdf.` |
| `attachment.quota.exceeded` | `Cuota de adjuntos superada para el terreno {0}: el total no puede superar 100 MB.` |
| `cadastral.api.unavailable` | `El servicio catastral externo no está disponible. Vuelva a intentarlo más tarde.` |
| `user.notfound` | `El usuario con ID {0} no existe.` |

---

## 11. Almacenamiento de adjuntos

- Implementación actual: **volumen local** (`LocalFileStorageService`).
- Variable: `ATTACHMENTS_STORAGE_ROOT` (default `./attachments`).
- Estructura: `<root>/<terrainId>/<random-uuid>-<original_name>`.
- La interfaz `FileStorageService` permite swap futuro a S3/MinIO sin tocar `AttachmentService` ni controllers.

> Si despliegas en Docker, monta un volumen persistente en `${ATTACHMENTS_STORAGE_ROOT}` o perderás los adjuntos en cada reinicio.

---

## 12. Configuración relevante

`application.properties` (común; perfiles `dev` y `prod` los sobrescriben):

```properties
server.port=${SERVER_PORT:8083}
spring.datasource.url=jdbc:postgresql://localhost:5432/terrain_db
spring.flyway.enabled=true
spring.jpa.database-platform=org.hibernate.spatial.dialect.postgis.PostgisDialect
grpc.client.auth-service.address=static://auth-service:9091
grpc.server.port=9093
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=terrain-service-group
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=12MB
attachments.storage.root=${ATTACHMENTS_STORAGE_ROOT:./attachments}
# HU-TER-05 — endpoints externos (vacíos por defecto → 502 controlado)
cadastro.api.base-url=
sigpac.api.base-url=
```

Variables de entorno habituales en producción:

| Variable | Uso |
|---|---|
| `SERVER_PORT` | Puerto HTTP del microservicio |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Conexión a `terrain-db` |
| `KAFKA_BROKER` | Host:puerto del broker Kafka |
| `ATTACHMENTS_STORAGE_ROOT` | Raíz del volumen de adjuntos |
| `CADASTRO_API_BASE_URL` / `SIGPAC_API_BASE_URL` | Endpoints reales de catastro/SIGPAC (cuando estén disponibles) |

---

## 13. Testing y QA

El servicio incluye dos suites complementarias: tests JVM (Maven) que se corren en CI, y un script de QA basado en `curl/grpcurl/kcat` para validar el sistema corriendo end-to-end.

### 13.1 Tests Maven (`./mvnw -pl terrain-service test`)

180 tests verdes (169 ejecutados + 11 skipped por motivos documentados) cubriendo:

| Capa | Fichero(s) | Descripción |
|---|---|---|
| WebMvc slice | `controller/TerrainControllerTest.java`, `controller/AttachmentControllerTest.java` | 74 tests sobre el contrato HTTP (status, headers, ProblemDetail, validación DTO). |
| Unit (Mockito) | `service/TerrainServiceTest.java`, `service/AttachmentServiceTest.java`, `service/CadastralImportServiceTest.java`, `service/EventPublisherTest.java`, `service/LocalFileStorageServiceTest.java` | 59 tests sobre la capa service. |
| Unit auxiliar | `utils/FieldsValidatorTest.java`, `grpc/TerrainGrpcServiceTest.java`, `listener/UserDeletedListenerTest.java` | 16 tests sobre proyección dinámica, gRPC y listener Kafka aislados. |
| JDBC slice (H2) | `repository/TerrainRepositoryTest.java` | 9 tests sobre el repositorio sin PostGIS. |
| WireMock | `service/CadastralImportServiceWireMockTest.java` | 9 tests del cliente HTTP a Catastro/SIGPAC con stubs (TER-9.13–9.22). |
| EmbeddedKafka | `integration/UserDeletedListenerKafkaTest.java` | 2 tests end-to-end del listener `user-deleted` con broker en proceso. |
| Testcontainers PostGIS | `integration/TerrainPostgisIntegrationTest.java` | 10 tests sobre constraints SQL reales (área, SRID, geom_valid, cascade). Auto-skip si Docker no está disponible. |

Comandos útiles:
```bash
./mvnw -pl terrain-service test                  # rápido, sin Docker
./mvnw -pl terrain-service verify                # incluye JaCoCo
./mvnw -pl terrain-service test -Dtest=TerrainPostgisIntegrationTest  # solo integración PostGIS
```

### 13.2 Plan de tests trazable: `terrain-service-test-plan.md`

Documento normativo que enumera todos los casos QA con un ID estable `TER-X.YY`. Cada test JVM lleva ese ID en su `@DisplayName` y el script de QA usa el mismo ID en su salida, así puedes mapear fallos 1:1 entre las tres capas (plan ↔ JUnit ↔ script).

### 13.3 Script QA end-to-end: `test-terrain-plan.sh`

Script bash que ejecuta los casos del plan que son verificables desde fuera del proceso. Pensado para validar el sistema corriendo (terrain-service + auth-service + gateway + Kafka). Detecta automáticamente las herramientas disponibles (`grpcurl` para §10, `kcat` para §11) y marca como `[SKIP]` cualquier sección sin las dependencias instaladas.

```bash
# Configuración por defecto: API en :8080, gateway en :9000, gRPC en :9093
./terrain-service/test-terrain-plan.sh

# Cambiar puertos
API_URL=http://localhost:8083 GATEWAY_URL=http://localhost:9001 ./test-terrain-plan.sh

# Saltar los casos de cuota >50 MB (requieren ~110 MB de subidas)
SKIP_SLOW=1 ./test-terrain-plan.sh

# Solo algunas secciones del plan
ONLY="1 2 9" ./test-terrain-plan.sh

# Con JWT precargado o autologin para §13
JWT="<token>" ./test-terrain-plan.sh
USER_EMAIL=foo@bar.com USER_PASSWORD=secreto ./test-terrain-plan.sh
```

Salida: `[PASS]` / `[FAIL]` / `[SKIP]` por caso, resumen al final, exit code `0` si todo pasó (`1` si hubo algún fail, `2` si faltan herramientas obligatorias).

### 13.4 Tests del gateway (sección 13 del plan)

Los casos `TER-13.01–13.06` (validación JWT, circuit breaker) viven en el módulo `api-gateway`, no aquí. Está documentado en `src/test/resources/GATEWAY_TESTS_NOTE.md` qué casos quedan pendientes en el otro módulo.

---

## Apéndice — Plantilla de pruebas con `curl`

```bash
JWT=...      # el token se obtiene en POST /auth/login
UID=...      # user_id propietario
TID=...      # terrain_id (rellenar tras crear)

# Crear terreno
curl -X POST http://localhost:9000/api/terrain \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{
    "name":"Olivar Norte","user_id":"'$UID'",
    "geometry":{"type":"Polygon","coordinates":[[[-3.71,40.42],[-3.71,40.43],[-3.70,40.43],[-3.70,40.42],[-3.71,40.42]]]},
    "soil_type":"franco","slope_percent":4.5,"irrigation":"goteo"
  }'

# Listar terrenos del usuario, sólo id+name+area
curl "http://localhost:9000/api/terrain?user_id=$UID&fields=id,name,area_m2" -H "Authorization: Bearer $JWT"

# Subir adjunto
curl -X POST "http://localhost:9000/api/terrain/$TID/attachment?user_id=$UID" \
  -H "Authorization: Bearer $JWT" -F "file=@plano.pdf;type=application/pdf"

# Importar desde catastro (no persiste)
curl -X POST http://localhost:9000/api/terrain/import \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"reference":"9872023VH5797S0001WX","kind":"CADASTRAL"}'

# Borrar terreno (dispara cascada SQL en attachments + Kafka terrain-deleted)
curl -X DELETE "http://localhost:9000/api/terrain/$TID?user_id=$UID" -H "Authorization: Bearer $JWT"
```
