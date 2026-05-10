# Documentación de `season-service`

> Documentación técnica única del microservicio `season-service`: contratos REST, esquema de BBDD, integración cross-servicio, flujos y operación.
>
> **Última actualización:** 2026-05-10 (rama `feat-season-service`, commits `e1bf871..35d52e4`).
> **Versión Spring Boot:** 3.4.0 · **Java:** 21
> **Estado:** documentación viva — refleja el código tras cerrar las deudas SS-1..SS-10 del `SEASON_SERVICE_IMPLEMENTATION_PLAN.md`.

---

## Índice

1. [Visión general y rol en el ecosistema](#1-visión-general-y-rol-en-el-ecosistema)
2. [Configuración y arranque](#2-configuración-y-arranque)
3. [Esquema de base de datos](#3-esquema-de-base-de-datos)
4. [Endpoints REST](#4-endpoints-rest)
5. [Cliente gRPC (no expone gRPC)](#5-cliente-grpc-no-expone-grpc)
6. [Kafka: eventos consumidos](#6-kafka-eventos-consumidos)
7. [Validación, errores y status HTTP](#7-validación-errores-y-status-http)
8. [Internacionalización (i18n)](#8-internacionalización-i18n)
9. [Flujos de trabajo](#9-flujos-de-trabajo)
10. [Relación con el resto de microservicios](#10-relación-con-el-resto-de-microservicios)
11. [Ejemplos `curl` listos para copiar](#11-ejemplos-curl-listos-para-copiar)
12. [Operación y troubleshooting](#12-operación-y-troubleshooting)

---

## 1. Visión general y rol en el ecosistema

`season-service` modela las **temporadas de cultivo**: cada `season` es una franja temporal en la que un cultivo (`crop`) se siembra sobre un terreno (`terrain`).

**Patrón arquitectónico:** `season` actúa como **tabla intermedia (associative entity)** en una relación **N:N** entre `terrain` y `crop`, pero con atributos propios (`start_date`, `end_date`, `season_type_id`, `observations`) que la convierten en una entidad de pleno derecho.

**Cardinalidad:**

```
terrain (1) ───< season (N) >─── (1) crop
```

- 1 terreno puede tener **muchas** temporadas históricas (rotación de cultivos).
- 1 temporada pertenece a **un único** terreno y a **un único** cultivo.
- 1 cultivo del catálogo puede aparecer en **muchas** temporadas a lo largo del tiempo.

**Reglas de integridad cross-BBDD** (suplen las FKs SQL imposibles entre BBDD distintas):

| Operación | Mecanismo | Garantía |
|---|---|---|
| `POST /season` | gRPC sincrónico a `terrain-service.CheckTerrainExists` y `crop-service.CheckCropExists` antes del INSERT | "No creo seasons huérfanas." |
| Borrar un terreno | Kafka asíncrono — `terrain-service` publica `terrain-deleted` → este servicio escucha y borra en cascada las seasons del terreno | "Cuando un terreno desaparece, sus temporadas se limpian." |
| Borrar un cultivo | **Sin propagación** — `crop-service` no publica Kafka y este servicio no tiene listener `crop-deleted` | "Las temporadas son registros históricos: si alguien limpia el catálogo de cultivos, las seasons se conservan con su `crop_id` aunque ya no resuelva." |

**Lo que NO hace y por qué:**

- **No expone servidor gRPC.** Solo es cliente (consume `terrain-service:9093` y `crop-service:9094`).
- **No publica eventos Kafka.** Solo consume `terrain-deleted`.
- **No tiene `user_id` propio.** La autorización por usuario se delega al terreno (la season "pertenece" a quien sea dueño del terreno; verificar la propiedad antes del POST está fuera de alcance — ver `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md`).
- **No expone CRUD de escritura para `season_type`.** Los 4 tipos (`Planting/Harvest/Fallow/Dormancy`) son seeds del `V1`.

---

## 2. Configuración y arranque

### 2.1 Puertos

| Recurso | Puerto host | Puerto contenedor |
|---|---|---|
| HTTP REST | `8082` | `8082` |
| BBDD PostgreSQL (`season-db`) | `5434` | `5432` |
| (no expone gRPC) | — | — |

### 2.2 Variables de entorno

| Variable | Default (perfil `dev`) | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` / `prod` |
| `SERVER_PORT` | `8082` | puerto HTTP del servicio |
| `DB_URL` | `jdbc:postgresql://localhost:5434/season_db` | URL JDBC |
| `DB_USER` | `postgres` | usuario BBDD |
| `DB_PASSWORD` | `postgres` | clave BBDD |
| `KAFKA_BROKER` | `localhost:9092` (vía `spring.kafka.bootstrap-servers`) | broker Kafka |

En `prod` (Docker), `DB_URL` apunta a `season-db:5432/season_db`. Los gRPC clients resuelven a `terrain-service:9093` y `crop-service:9094` por la red interna `agro-net`.

### 2.3 Arranque

**Modo desarrollo (BBDD aparte, app local):**

```bash
docker compose up -d season-db kafka terrain-service crop-service auth-service
./mvnw -pl season-service spring-boot:run -Dspring-boot.run.profiles=dev
```

> Para que el flujo gRPC funcione en dev local, los servicios `terrain-service` y `crop-service` deben estar accesibles en `localhost:9093` y `localhost:9094` respectivamente. Si los levantas vía Docker, los puertos gRPC deben estar publicados al host.

**Modo Docker completo:**

```bash
docker compose up --build season-service
```

Flyway aplica `V1__create_season_table.sql` automáticamente al primer arranque (idempotente).

### 2.4 Imagen Docker

Multi-stage:

- **Build:** `eclipse-temurin:21-jdk-jammy` (Ubuntu/glibc — necesario para `protoc-gen-grpc-java`).
- **Runtime:** `eclipse-temurin:21-jre-alpine` (imagen ligera).

---

## 3. Esquema de base de datos

Migración única hasta hoy: `season-service/src/main/resources/db/migration/V1__create_season_table.sql`.

### 3.1 Tabla `season_type`

```sql
CREATE TABLE season_type (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT
);
```

**Seeds insertados por la migración:**

| id | name |
|---|---|
| 1 | `Planting` |
| 2 | `Harvest` |
| 3 | `Fallow` |
| 4 | `Dormancy` |

> Los IDs son `SERIAL`. Aunque hoy se asignan 1-4 por el orden del `INSERT`, **no los hardcodees** en clientes; el plan futuro (HU-CUL-01) podría introducir variantes.

### 3.2 Tabla `season`

```sql
CREATE TABLE season (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    terrain_id     UUID    NOT NULL,                    -- FK lógica → terrain.id (otra BBDD)
    crop_id        UUID    NOT NULL,                    -- FK lógica → crop.id    (otra BBDD)
    start_date     DATE    NOT NULL,
    end_date       DATE,
    season_type_id INTEGER REFERENCES season_type(id),
    observations   TEXT,
    CONSTRAINT start_before_end CHECK (end_date IS NULL OR end_date >= start_date)
);
```

| Columna | Tipo SQL | Constraints | Notas |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | generado por Postgres |
| `terrain_id` | `UUID` | `NOT NULL` | sin FK SQL (vive en `terrain-db`); validación gRPC en INSERT |
| `crop_id` | `UUID` | `NOT NULL` | sin FK SQL (vive en `crop-db`); validación gRPC en INSERT |
| `start_date` | `DATE` | `NOT NULL` | fecha de inicio de la temporada |
| `end_date` | `DATE` | nullable | fecha de fin estimada / efectiva |
| `season_type_id` | `INTEGER` | FK → `season_type(id)` (nullable) | clasificación del tipo de actividad |
| `observations` | `TEXT` | nullable | acotado a 2000 chars en la API |
| (constraint) | `start_before_end` | `end_date >= start_date` | reforzado además en el DTO con `@AssertTrue` |

**Estrategia de borrado en cascada:**

- Una `season` **no se borra** cuando se borra el `crop` que referencia (R5). El `crop_id` queda como referencia colgante; las consultas que enriquezcan vía gRPC `CheckCropExists` recibirán `false` pero la `season` permanece en la BBDD.
- Una `season` **sí se borra** cuando se borra el `terrain` que referencia (R4). El borrado lo dispara el listener Kafka `TerrainDeletedListener` ejecutando `DELETE FROM season WHERE terrain_id = ?`.

---

## 4. Endpoints REST

Base path: `/season` (sin prefijo `/api`; el `api-gateway` añade `/api` y aplica `StripPrefix=1`).

| Método | Ruta | Descripción | Status éxito |
|---|---|---|---|
| `GET` | `/season/{id}` | Detalle de una season por id | 200 |
| `GET` | `/season/terrain/{terrainId}` | Listar seasons de un terreno (orden `start_date DESC`) | 200 |
| `POST` | `/season` | Crear una season | 201 |
| `DELETE` | `/season/{id}` | Borrar una season | 204 |

> El gateway aplica `JwtValidation` a `/api/season/**` (única ruta del proyecto que hoy lo exige). El servicio internamente confía en que el gateway ya validó.

### 4.1 `GET /season/{id}`

Devuelve el detalle de una season.

**Path param:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `id` | UUID | sí |

**Query params:**

| Param | Tipo | Obligatorio | Default | Descripción |
|---|---|---|---|---|
| `fields` | CSV de `SeasonField` | no | todas las columnas (`*`) | Whitelist: `id, terrain_id, crop_id, start_date, end_date, season_type_id, observations` |

**Respuesta 200:** `application/json` con el objeto JSON. Las claves coinciden con las columnas de BBDD.

**Errores:**

| Status | Causa |
|---|---|
| 400 | `id` con formato incorrecto (no es UUID) — binding error |
| 400 | `fields` con un valor fuera del enum `SeasonField` |
| 404 | la season con ese `id` no existe |

**Ejemplo de respuesta:**

```json
{
  "id": "8a4f3c8e-1234-4abc-9def-0123456789ab",
  "terrain_id": "11111111-1111-1111-1111-111111111111",
  "crop_id": "22222222-2222-2222-2222-222222222222",
  "start_date": "2025-03-01",
  "end_date": "2025-08-01",
  "season_type_id": 1,
  "observations": "Siembra de trigo blando"
}
```

### 4.2 `GET /season/terrain/{terrainId}`

Lista todas las seasons de un terreno (histórico) ordenadas por `start_date DESC`.

**Path param:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `terrainId` | UUID | sí |

**Query params:** mismo `fields` que en `/season/{id}`.

**Respuesta 200:** `application/json` con un array. Si el terreno no tiene seasons, devuelve `[]` (no 404).

> **Importante:** este endpoint **no valida** que el terreno exista (no llama a `terrain-service.CheckTerrainExists`). Si pasas un `terrainId` inexistente, devuelve `[]`. La validación de existencia podría añadirse a futuro si el frontend lo requiere para mostrar mensajes específicos.

### 4.3 `POST /season`

Crea una nueva season.

**Headers:** `Content-Type: application/json`.

**Body — `SeasonRequest`:**

| Campo | Tipo | Obligatorio | Validación | Mensaje i18n key |
|---|---|---|---|---|
| `terrain_id` | UUID | sí | `@NotNull`; debe existir en `terrain-service` (gRPC) | `season.terrain.required`, `season.terrain.notfound` |
| `crop_id` | UUID | sí | `@NotNull`; debe existir en `crop-service` (gRPC) | `season.crop.required`, `season.crop.notfound` |
| `start_date` | string ISO-8601 (`YYYY-MM-DD`) | sí | `@NotNull` | `season.start.required` |
| `end_date` | string ISO-8601 | no | si presente, `>= start_date` (`@AssertTrue`) | `season.dates.range` |
| `season_type_id` | integer | no | si presente, debe existir en `season_type` (FK SQL) | — |
| `observations` | string | no | `@Size(max=2000)` | `season.observations.size` |

**Respuesta 201:** `application/json` con el `UUID` generado, codificado como string JSON:

```json
"8a4f3c8e-1234-4abc-9def-0123456789ab"
```

**Errores:**

| Status | Causa | Body |
|---|---|---|
| 400 | Validación de campos (Bean Validation o `@AssertTrue`) | `ProblemDetail` con `errors[]` |
| 404 | `terrain_id` no existe en `terrain-service` | `ProblemDetail{title:"Terrain not found"}` |
| 404 | `crop_id` no existe en `crop-service` | `ProblemDetail{title:"Crop not found"}` |
| 400 | `season_type_id` inexistente (FK SQL) | `ProblemDetail` genérico |

> El orden de validación es fijo: terrain primero, crop después. Si ambos fallan, solo se reporta el de terrain.

### 4.4 `DELETE /season/{id}`

Borra una season manualmente (operación fuera de la cascada Kafka).

**Path param:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `id` | UUID | sí |

**Respuesta 204:** `No Content` **sin body**.

**Errores:**

| Status | Causa |
|---|---|
| 400 | `id` con formato incorrecto |
| 404 | la season no existe |

> El borrado **no** publica ningún evento Kafka. No hay servicios downstream que dependan del borrado individual de una season.

---

## 5. Cliente gRPC (no expone gRPC)

`season-service` **no expone** servidor gRPC. Solo es cliente de dos servicios externos.

### 5.1 Cliente — `terrain-service.CheckTerrainExists`

| Atributo | Valor |
|---|---|
| Servidor remoto | `terrain-service:9093` (Docker) / `localhost:9093` (dev) |
| Proto local | `season-service/src/main/proto/terrain.proto` (copia del de `terrain-service`) |
| Cliente | `grpc/TerrainGrpcClient.java` (`@GrpcClient("terrain-service")`) |
| Método | `CheckTerrainExists(TerrainIdRequest{terrain_id:string}) → TerrainExistsResponse{exists:bool}` |
| Cuándo se invoca | En `SeasonService.createSeason()` antes del INSERT |
| Negotiation | `plaintext` |

Si el RPC lanza una `Exception` (servidor caído, timeout, etc.), `TerrainGrpcClient` la envuelve en `RuntimeException("Failed to verify terrain existence")` → 500 al cliente HTTP. **Mejorable** a futuro con retry / circuit-breaker.

### 5.2 Cliente — `crop-service.CheckCropExists`

| Atributo | Valor |
|---|---|
| Servidor remoto | `crop-service:9094` (Docker) / `localhost:9094` (dev) |
| Proto local | `season-service/src/main/proto/crop.proto` (copia del de `crop-service`) |
| Cliente | `grpc/CropGrpcClient.java` (`@GrpcClient("crop-service")`) |
| Método | `CheckCropExists(CropIdRequest{crop_id:string}) → CropExistsResponse{exists:bool}` |
| Cuándo se invoca | En `SeasonService.createSeason()` antes del INSERT (después del check de terrain) |
| Negotiation | `plaintext` |

> **Hot-fix corregido en este branch:** `application-prod.properties` tenía `static://crop-se:9094` (typo). Ahora es `static://crop-service:9094`.

---

## 6. Kafka: eventos consumidos

### 6.1 Topic `terrain-deleted`

| Atributo | Valor |
|---|---|
| Productor | `terrain-service.EventPublisher.publishTerrainDeleted()` |
| Topic | `terrain-deleted` |
| Payload del productor | `com.agro.terrainservice.event.TerrainDeletedEvent { UUID terrainId }` |
| Listener | `listener/TerrainDeletedListener.java` |
| Group ID | `season-service-group` |
| Acción local | `SeasonService.deleteSeasonsByTerrainId(terrainId)` → `DELETE FROM season WHERE terrain_id = ?` |
| `auto-offset-reset` | `earliest` |

**Type mapping** (`application.properties`):

```properties
spring.kafka.consumer.properties.spring.json.use.type.headers=true
spring.kafka.consumer.properties.spring.json.type.mapping=\
  com.agro.terrainservice.event.TerrainDeletedEvent:com.agro.seasonservice.event.TerrainDeletedEvent
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=RECORD
```

> **Garantía de "at-least-once":** con `enable-auto-commit=false` + `ack-mode=RECORD`, el offset solo avanza tras completar el listener (transacción JDBC incluida). Si el proceso muere a mitad del DELETE, el mensaje se vuelve a procesar al reiniciar — el `DELETE FROM ... WHERE terrain_id = ?` es **idempotente** (un re-procesamiento no causa daño).

### 6.2 Topics no consumidos

- **`crop-deleted`** — **no se consume**. Es deliberado (R5): borrar un cultivo no afecta a las seasons que lo referenciaron históricamente.
- **`user-deleted`** — **no se consume**. La cascada user → terrain → season pasa por `terrain-service`, que reacciona al `user-deleted` borrando los terrenos del usuario y emitiendo un `terrain-deleted` por cada uno; este servicio solo necesita escuchar el segundo eslabón.

---

## 7. Validación, errores y status HTTP

Todas las respuestas de error usan `ProblemDetail` (RFC 7807) con `Content-Type: application/problem+json`.

### 7.1 Excepciones gestionadas (`GlobalExceptionHandler`)

| Excepción | Status | Title | Origen típico |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `Campos invalido` | Bean Validation falla en `POST /season` |
| `InvalidFieldException` | 400 | `Campos invalido` | `?fields=` con un valor fuera del enum `SeasonField` |
| `TerrainNotFoundException` | 404 | `Terrain not found` | gRPC `CheckTerrainExists` devuelve `false` |
| `CropNotFoundException` | 404 | `Crop not found` | gRPC `CheckCropExists` devuelve `false` |
| `ResourceNotFoundException` | 404 | `Resource not found` | season buscada/borrada no existe |
| `IllegalArgumentException` | 400 | `Invalid argument` | binding errors no cubiertos por las anteriores |

### 7.2 Errores no gestionados explícitamente

Cualquier `RuntimeException` no listada → handler default de Spring → 500 con `ProblemDetail` genérico. En particular:

- Fallo gRPC al llamar a `terrain-service` o `crop-service` → `RuntimeException("Failed to verify ...")` → 500. Los clientes deben tratarlos como transitorios y reintentar con backoff.
- `DataIntegrityViolationException` (p. ej. `season_type_id` inexistente) → 500 hoy; podría mapearse a 400 en una iteración futura.

### 7.3 Validación de DTO

`SeasonRequest`:

```java
public record SeasonRequest(
        @NotNull(message = "{season.terrain.required}") UUID terrain_id,
        @NotNull(message = "{season.crop.required}")    UUID crop_id,
        @NotNull(message = "{season.start.required}")   LocalDate start_date,
        LocalDate end_date,
        Integer season_type_id,
        @Size(max = 2000, message = "{season.observations.size}") String observations
) {
    @AssertTrue(message = "{season.dates.range}")
    public boolean isEndAfterStart() {
        return end_date == null || start_date == null || !end_date.isBefore(start_date);
    }
}
```

### 7.4 Whitelist de campos para `?fields=`

Definida en `constants/SeasonField.java`:

```java
public enum SeasonField {
    id, terrain_id, crop_id, start_date, end_date, season_type_id, observations
}
```

`fields` se parsea como `List<SeasonField>` (Spring binding). Cualquier valor fuera del enum → 400.

> El whitelist es la **única** defensa contra inyección SQL en el SELECT dinámico. **Nunca** añadir nuevas columnas al SELECT pasando estructuras dinámicas — siempre actualizar el enum.

---

## 8. Internacionalización (i18n)

Idiomas soportados: **español (`es`)** por defecto y **inglés (`en`)** como fallback para locales no traducidas.

El idioma se resuelve por la cabecera `Accept-Language`. Si no se envía, se usa `es` (configurado en `I18nConfig`).

**Ficheros:**

- `src/main/resources/i18n/messages_es.properties`
- `src/main/resources/i18n/messages.properties` (EN, fallback)

**Claves disponibles:**

| Clave | ES | EN |
|---|---|---|
| `season.not.found` | La temporada no existe | Season not found |
| `season.terrain.notfound` | El terreno con ID `{0}` no existe | Terrain with ID `{0}` does not exist |
| `season.crop.notfound` | El cultivo con ID `{0}` no existe | Crop with ID `{0}` does not exist |
| `season.dates.range` | La fecha de fin no puede ser anterior a la fecha de inicio | end_date must be on or after start_date |
| `season.created` | Temporada creada | Season created |
| `season.deleted` | Temporada eliminada | Season deleted |
| `season.terrain.required` | El identificador del terreno es obligatorio | terrain id is required |
| `season.crop.required` | El identificador del cultivo es obligatorio | crop id is required |
| `season.start.required` | La fecha de inicio es obligatoria | start date is required |
| `season.observations.size` | Las observaciones no pueden superar 2000 caracteres | observations must not exceed 2000 characters |

---

## 9. Flujos de trabajo

### 9.1 Alta de una season (POST /season)

```
Cliente HTTP        api-gateway       season-service       terrain-service     crop-service       season-db
   │                    │                  │                    │                  │                  │
   │ POST /api/season   │                  │                    │                  │                  │
   │ + Authorization    │                  │                    │                  │                  │
   ├───────────────────>│                  │                    │                  │                  │
   │                    │ JwtValidation    │                    │                  │                  │
   │                    │ /validate        │                    │                  │                  │
   │                    │ StripPrefix=1    │                    │                  │                  │
   │                    │ → POST /season   │                    │                  │                  │
   │                    ├─────────────────>│                    │                  │                  │
   │                    │                  │ Bean Validation    │                  │                  │
   │                    │                  │ + @AssertTrue      │                  │                  │
   │                    │                  │ gRPC               │                  │                  │
   │                    │                  │ CheckTerrainExists │                  │                  │
   │                    │                  ├───────────────────>│                  │                  │
   │                    │                  │<─── exists=true ───┤                  │                  │
   │                    │                  │ gRPC               │                  │                  │
   │                    │                  │ CheckCropExists    │                  │                  │
   │                    │                  ├──────────────────────────────────────>│                  │
   │                    │                  │<─── exists=true ──────────────────────┤                  │
   │                    │                  │ INSERT INTO season RETURNING id       │                  │
   │                    │                  ├─────────────────────────────────────────────────────────>│
   │                    │                  │<──────────── id ────────────────────────────────────────┤
   │                    │<─── 201 "<uuid>" │                    │                  │                  │
   │<─── 201 "<uuid>" ──┤                  │                    │                  │                  │
```

**Camino de error (`crop_id` inexistente):**

```
                                  gRPC CheckCropExists → exists=false
                                            │
                                            ▼
                              throw new CropNotFoundException("...")
                                            │
                                            ▼
                       GlobalExceptionHandler → 404 ProblemDetail
                                  title: "Crop not found"
```

### 9.2 Borrado en cascada vía Kafka (terrain → seasons)

Disparado por el borrado de un terreno en `terrain-service`. La saga completa cuando el origen es la baja de un usuario (RGPD):

```
auth-service              terrain-service                  season-service              season-db
     │                          │                                │                          │
     │ DELETE /users/{id}       │                                │                          │
     ├─────── (cliente) ────────┤                                │                          │
     │ commit auth-db           │                                │                          │
     │                          │                                │                          │
     │ Kafka: user-deleted      │                                │                          │
     ├─────────────────────────>│ UserDeletedListener            │                          │
     │                          │ por cada terrain del user:     │                          │
     │                          │   DELETE FROM terrain WHERE…   │                          │
     │                          │   commit                       │                          │
     │                          │   publish terrain-deleted(t.id)│                          │
     │                          ├───────────────────────────────>│ TerrainDeletedListener   │
     │                          │                                │ deleteSeasonsByTerrainId │
     │                          │                                ├─────────────────────────>│
     │                          │                                │     DELETE FROM season   │
     │                          │                                │     WHERE terrain_id = ? │
     │                          │                                │<─────── rowcount ────────┤
     │                          │                                │ ack al broker (RECORD)   │
```

**Garantía de consistencia eventual:** el momento exacto en que las seasons desaparecen depende del lag del consumer Kafka, pero típicamente es < 1 s en un cluster sano.

### 9.3 Borrado manual de una season

```
Cliente              api-gateway              season-service              season-db
   │                    │                         │                          │
   │ DELETE /api/season/{id}                      │                          │
   ├───────────────────>│ JwtValidation           │                          │
   │                    │ → DELETE /season/{id}   │                          │
   │                    ├────────────────────────>│ DELETE FROM season       │
   │                    │                         │ WHERE id = ?             │
   │                    │                         ├─────────────────────────>│
   │                    │                         │<──── rows = 1 ───────────┤
   │<──── 204 ──────────┤                         │                          │
```

Si `rows == 0` → `ResourceNotFoundException` → 404.

### 9.4 Borrado de un cultivo (no afecta)

```
Admin → DELETE /api/crop/{id}
              │
              ▼
        crop-service borra el row de crop-db
              │
              ▼
        (fin — no se publica nada)

season-service                ← no se entera, no actúa
season-db                     ← inalterada; rows con crop_id apuntan ahora a un id huérfano
```

Esto es **el comportamiento intencional** (R5): las seasons son registros históricos.

### 9.5 Lifecycle de la BBDD

```
docker compose up season-db
   │
   ▼
Postgres arranca con db=season_db
   │
   ▼
season-service arranca
   │
   ▼
Flyway lee classpath:db/migration
   │
   ├── V1__create_season_table.sql aplicado
   │       (crea season_type, season, seeds de tipos)
   │
   ▼
flyway_schema_history actualizado
   │
   ▼
Servicio escuchando :8082
Kafka consumer en `terrain-deleted` listo
gRPC clients listos para llamar a terrain-service y crop-service
```

---

## 10. Relación con el resto de microservicios

### 10.1 Mapa de dependencias

```
                        ┌─────────────┐
                        │ api-gateway │
                        │   :9000     │
                        └──────┬──────┘
                               │
                          /api/season/**
                          + JwtValidation
                               │
                               ▼
                        ┌────────────┐
                        │  season-   │
                        │  service   │
                        │   :8082    │
                        └─────┬──────┘
                              │
        ┌─────────────────────┼────────────────────┐
        │ gRPC :9093          │ gRPC :9094         │ Kafka
        │ CheckTerrainExists  │ CheckCropExists    │ terrain-deleted
        ▼                     ▼                    ▼ (consumer)
   ┌──────────┐          ┌──────────┐         ┌────────────┐
   │ terrain- │          │  crop-   │         │ Kafka      │
   │ service  │          │ service  │         │ broker     │
   │  :8080   │          │  :8081   │         │ :9092      │
   └──────────┘          └──────────┘         └────────────┘
                                                    ▲
                                                    │ Producer:
                                                    │ terrain-service
                                                    │ (cuando borra terreno)
        │
        ▼
   ┌──────────┐
   │ season-  │
   │ db :5434 │
   └──────────┘
```

### 10.2 ¿Quién depende de `season-service`?

| Quién | Cómo lo consume | Frecuencia | Crítico |
|---|---|---|---|
| `api-gateway` | Reverse proxy de `/api/season/**` con `StripPrefix=1`, `JwtValidation` y circuit breaker `seasonService` | toda llamada externa | sí (si season-service cae, el circuit breaker dispara fallback 503) |
| Frontend / clientes externos | Vía `/api/season/**` con JWT | depende del uso | UX-crítico |
| (futuro) HU-CUL-03/04 | El cuaderno de explotación cruzaría seasons + treatments | aún no implementado | — |

### 10.3 ¿De qué depende `season-service`?

| Quién | Cómo | Tipo | Notas |
|---|---|---|---|
| `season-db` | JDBC directo, transaccional | duro | sin él no arranca (Flyway no aplica) |
| `terrain-service` | gRPC `CheckTerrainExists` síncrono en cada `POST /season` | duro | si está caído → 500 al cliente |
| `crop-service` | gRPC `CheckCropExists` síncrono en cada `POST /season` | duro | si está caído → 500 al cliente |
| Kafka broker | Consumer del topic `terrain-deleted` | blando | si está caído, el listener no procesa pero los POST/GET/DELETE siguen funcionando; al recuperar, el offset retoma donde quedó |
| `auth-service` | **Indirecta**: el gateway valida JWT contra `/auth/validate` antes de proxiar | blando | si está caído, todas las peticiones autenticadas devuelven 401/503 desde el gateway |

> **No hay dependencia directa con `auth-service` desde el código de `season-service`.** No conoce ni el `user_id` del solicitante (eso está identificado como deuda en `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md`).

### 10.4 Integridad referencial cross-BBDD (resumen)

| Operación | Mecanismo | Estado |
|---|---|---|
| INSERT season con terrain_id | gRPC `CheckTerrainExists` | ✅ implementado |
| INSERT season con crop_id | gRPC `CheckCropExists` | ✅ implementado |
| DELETE terrain → cascada en seasons | Kafka `terrain-deleted` + listener | ✅ implementado |
| DELETE crop → no afecta seasons | sin propagación (intencional) | ✅ es lo deseado |
| Validación de **propiedad** (terrain pertenece al solicitante) | — | ❌ no implementada (deuda transversal — `INVESTIGACION-…` §3) |

### 10.5 Qué pasa si season-service cae

| Operación afectada | Síntoma |
|---|---|
| `GET/POST/DELETE /api/season/*` desde el frontend | El gateway abre el circuit breaker y devuelve 503 vía `/fallback/season` |
| Borrado en cascada por `terrain-deleted` | El mensaje se queda en Kafka. Cuando season-service vuelve, lo procesa (offset earliest). Las seasons del terreno borrado quedan **temporalmente colgadas** hasta que el listener despierta. |
| `terrain-service`, `crop-service`, `auth-service` | Sin impacto — ninguno depende de season-service. |

---

## 11. Ejemplos `curl` listos para copiar

> Asumen que el `api-gateway` está en `localhost:9000` y tienes un JWT válido (obtenido en `POST /auth/login`). Para llamar directo al servicio sustituye `http://localhost:9000/api/season` por `http://localhost:8082/season` y omite el header `Authorization` (el servicio no lo valida internamente).

### 11.1 Variables de ejemplo

```bash
JWT="<token de /auth/login>"
TID="11111111-1111-1111-1111-111111111111"   # un terrain_id real
CID="22222222-2222-2222-2222-222222222222"   # un crop_id real
```

### 11.2 Crear una season

```bash
curl -s -X POST http://localhost:9000/api/season \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: es' \
  -d "{
    \"terrain_id\":\"$TID\",
    \"crop_id\":\"$CID\",
    \"start_date\":\"2025-03-01\",
    \"end_date\":\"2025-08-01\",
    \"season_type_id\":1,
    \"observations\":\"Siembra de trigo blando\"
  }"
# 201 Created
# "<uuid generado>"
```

### 11.3 Detalle de una season

```bash
SID="<uuid devuelto por el POST anterior>"
curl -s -H "Authorization: Bearer $JWT" \
     "http://localhost:9000/api/season/$SID" | jq .
```

### 11.4 Detalle con proyección

```bash
curl -s -H "Authorization: Bearer $JWT" \
     "http://localhost:9000/api/season/$SID?fields=id,start_date,crop_id" | jq .
```

### 11.5 Listar todas las seasons de un terreno

```bash
curl -s -H "Authorization: Bearer $JWT" \
     "http://localhost:9000/api/season/terrain/$TID" | jq .
```

### 11.6 Borrar una season

```bash
curl -s -X DELETE -H "Authorization: Bearer $JWT" \
     "http://localhost:9000/api/season/$SID" -i
# HTTP/1.1 204 No Content
```

### 11.7 Casos de error típicos

**`terrain_id` inexistente** (gRPC dice `exists=false`):

```bash
curl -s -X POST http://localhost:9000/api/season \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d "{
    \"terrain_id\":\"00000000-0000-0000-0000-000000000000\",
    \"crop_id\":\"$CID\",
    \"start_date\":\"2025-03-01\"
  }"
# 404 Not Found
# {"title":"Terrain not found","status":404,
#  "detail":"El terreno con ID 00000000-... no existe"}
```

**`end_date < start_date`** (capturado por `@AssertTrue` antes de tocar BBDD):

```bash
curl -s -X POST http://localhost:9000/api/season \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d "{
    \"terrain_id\":\"$TID\",
    \"crop_id\":\"$CID\",
    \"start_date\":\"2025-08-01\",
    \"end_date\":\"2025-03-01\"
  }"
# 400 Bad Request
# {"title":"Campos invalido","status":400,
#  "errors":["isEndAfterStart: La fecha de fin no puede ser anterior a la fecha de inicio"]}
```

**Body vacío:**

```bash
curl -s -X POST http://localhost:9000/api/season \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d "{}"
# 400 Bad Request
# {"errors":[
#   "terrain_id: El identificador del terreno es obligatorio",
#   "crop_id: El identificador del cultivo es obligatorio",
#   "start_date: La fecha de inicio es obligatoria"
# ]}
```

**`fields` con campo no permitido:**

```bash
curl -s -H "Authorization: Bearer $JWT" \
     "http://localhost:9000/api/season/$SID?fields=secret" -i
# 400 Bad Request (binding error: SeasonField no acepta "secret")
```

---

## 12. Operación y troubleshooting

### 12.1 Resetear la BBDD localmente (perder datos)

```bash
docker compose down -v
docker compose up season-db
```

### 12.2 Forzar re-aplicación de migraciones sin afectar al resto

```bash
docker compose stop season-service season-db
docker volume rm tfg-back_db_season_data
docker compose up -d season-db
./mvnw -pl season-service spring-boot:run
```

### 12.3 Conectarse a la BBDD para inspección

```bash
PGPASSWORD=postgres psql -h localhost -p 5434 -U postgres -d season_db
season_db=# \dt
season_db=# SELECT * FROM season_type;
season_db=# SELECT id, terrain_id, crop_id, start_date, end_date FROM season;
```

### 12.4 Ejecutar los tests del módulo

```bash
# Tests unitarios y MockMvc (sin Docker)
./mvnw -pl season-service test \
  -Dtest='SeasonServiceTest,SeasonControllerTest,TerrainDeletedListenerTest'

# Suite completa (incluye contextLoads que requiere Docker para Testcontainers)
./mvnw -pl season-service -am test
```

### 12.5 Inspeccionar mensajes Kafka manualmente

Con `kcat` (alias `kafkacat`) instalado:

```bash
# Consumer de prueba sobre terrain-deleted
kcat -b localhost:9092 -t terrain-deleted -C -q -e -o beginning

# Publicar un evento de prueba (provocar cascada manual)
echo '{"terrainId":"11111111-1111-1111-1111-111111111111"}' | \
  kcat -b localhost:9092 -t terrain-deleted -P \
       -H "__TypeId__=com.agro.terrainservice.event.TerrainDeletedEvent"
```

> El header `__TypeId__` es lo que el `spring.json.type.mapping` del consumer usa para resolver al `TerrainDeletedEvent` local.

### 12.6 Build de la imagen Docker desde cero

```bash
docker build -f season-service/Dockerfile -t season-service season-service/
```

> Si ves `PROTOC FAILED: protoc-gen-grpc-java-1.63.0-linux-x86_64.exe: program not found`, comprueba que el stage de build usa `eclipse-temurin:21-jdk-jammy`, no `:21-jdk-alpine` (Alpine usa musl libc, incompatible con el binario glibc del plugin gRPC).

### 12.7 Diagnosticar un POST que falla con 500

Si `POST /api/season` devuelve 500, las causas habituales son:

1. **`terrain-service` o `crop-service` caído** (timeout/connection refused gRPC). Logs muestran `RuntimeException("Failed to verify ...")`. Confirmar con `grpcurl -plaintext localhost:9093 list` y `localhost:9094 list`.
2. **Typo en `application-prod.properties`** (`crop-se:9094`). Ya corregido en la rama `feat-season-service`. Verificar tras deploy.
3. **`season_type_id` inexistente** → `DataIntegrityViolationException` no mapeada. Mejora futura: añadir handler `@ExceptionHandler(DataIntegrityViolationException.class) → 400`.
4. **Kafka no disponible** al arrancar el contexto si la auto-config requiere broker. En la práctica el broker es opcional para `POST /season` (no se publica nada), pero el listener tirará logs de "no broker".

### 12.8 Observabilidad

- **Logs:** stdout en formato Spring por defecto. Flyway loggea a nivel `INFO`. El listener Kafka loggea cada `TerrainDeletedEvent` recibido.
- **Health:** no hay actuator configurado. Para añadirlo, sumar `spring-boot-starter-actuator` al `pom.xml`.
- **Métricas:** no expuestas hoy.

---

> Si esta documentación deja de coincidir con el código, **manda un PR** con la corrección. Las fuentes de verdad son: el código en `feat-season-service`, este documento, `SEASON_SERVICE_IMPLEMENTATION_PLAN.md` para entender el "por qué" de los cambios recientes, y `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` para las decisiones de modelo.
