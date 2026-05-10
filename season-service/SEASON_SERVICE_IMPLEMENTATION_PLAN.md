# `season-service` — Plan de implementación / verificación

> **Rama:** `feat-season-service` (creada desde `main`, 2026-05-10).
> **Audiencia:** desarrollador (humano o agente) que va a aterrizar los cambios.
> **Contexto obligatorio antes de empezar:**
> - `Microservice-creation-rules.md` §6, §10, §11, §13, §14, §15
> - `project-guidelines.md` §4.5, §5
> - `season-service/INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` (auditoría previa)
> - `terrain-service/API_DOCUMENTATION.md` §6, §7
> - `crop-service/CROP_SERVICE_DOCUMENTACION.md` §5, §9
>
> **Naturaleza del documento:** este plan es **mayormente de verificación**. La mayoría de los requisitos del usuario **ya están implementados** en el código actual. El trabajo restante son fixes pequeños (un typo crítico en prod, error handling, i18n, alineación con las reglas del proyecto) más unos tests para tener confianza.

---

## Índice

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Reglas de dominio que pide el usuario](#2-reglas-de-dominio-que-pide-el-usuario)
3. [⚠ Aclaración sobre una contradicción aparente](#3-aclaración-sobre-una-contradicción-aparente)
4. [Estado actual del código (verificado)](#4-estado-actual-del-código-verificado)
5. [Contratos cross-servicio que season-service depende de](#5-contratos-cross-servicio-que-season-service-depende-de)
6. [Gaps detectados (severidad ordenada)](#6-gaps-detectados-severidad-ordenada)
7. [Plan de implementación paso a paso](#7-plan-de-implementación-paso-a-paso)
8. [Tests requeridos](#8-tests-requeridos)
9. [Definition of Done](#9-definition-of-done)
10. [Fuera de alcance (delegado a otros paquetes)](#10-fuera-de-alcance-delegado-a-otros-paquetes)
11. [Mapa de archivos tocados](#11-mapa-de-archivos-tocados)

---

## 1. Resumen ejecutivo

`season-service` modela la relación **N:N** entre `terrain` y `crop`, materializada en una tabla `season(terrain_id, crop_id, start_date, end_date, …)`. Cada `season` representa una franja temporal en la que un cultivo se planta en un terreno.

**Tras revisar el código, los contratos cross-servicio y los requisitos del usuario:**

- ✅ **El modelo de datos es correcto** y la cardinalidad está bien implementada (un terreno tiene N seasons, una season pertenece a 1 terreno y 1 crop).
- ✅ **La validación síncrona vía gRPC en POST /season ya existe** (`CheckTerrainExists` + `CheckCropExists` antes del INSERT).
- ✅ **El borrado en cascada vía Kafka cuando se borra un terreno ya funciona** (producer `terrain-service` → topic `terrain-deleted` → consumer `season-service.TerrainDeletedListener` → `deleteSeasonsByTerrainId`).
- ✅ **Borrar un crop NO afecta a las seasons** porque `crop-service` no publica ningún evento Kafka y `season-service` no tiene listener para ello. Esto es **el comportamiento que pides** y es correcto (las seasons son registros históricos; no deben desaparecer porque alguien limpie el catálogo de cultivos).

**Lo que falta** son cosas de calidad/robustez, no de arquitectura:

| Severidad | Cantidad | Naturaleza |
|---|---|---|
| 🔴 Alta | 1 | typo en `application-prod.properties` que rompe gRPC en prod |
| 🟠 Media | 4 | error handling, i18n hardcoded, `@Transactional` en listener Kafka, Dockerfile con Alpine en build (bug futuro de protoc) |
| 🟡 Baja | 5 | refactor de tipo Kafka, `Season` usa `Date` legacy, validación de fechas en DTO, deduplicación, tests |

**Cronograma sugerido:** un único paquete de cambios cubre toda la lista. Estimo 4–6 commits pequeños.

---

## 2. Reglas de dominio que pide el usuario

| # | Regla | Patrón |
|---|---|---|
| R1 | Un **terreno** puede tener **muchas** temporadas. | 1:N |
| R2 | Una **temporada** pertenece a **un único** terreno y a **un único** cultivo. | escalar `terrain_id`, `crop_id` |
| R3 | Un **cultivo** puede aparecer en **muchas** temporadas. | 1:N inversa |
| R4 | **Si se elimina un terreno**, todas las temporadas asociadas se borran (cascada). | Kafka asíncrono |
| R5 | **Si se elimina un cultivo**, **no debe afectar** a las temporadas existentes. | sin propagación |
| R6 | **POST /season** debe verificar que el `terrain_id` y el `crop_id` existen **antes** de insertar. | gRPC síncrono |

> **`season` actúa como tabla intermedia (junction table) en una relación N:N entre `terrain` y `crop`,** pero con atributos propios (`start_date`, `end_date`, `season_type_id`, `observations`) que la convierten en una entidad de pleno derecho, no una mera tabla de unión. Es el patrón clásico "*associative entity*".

---

## 3. ⚠ Aclaración sobre una contradicción aparente

Tu descripción incluye literalmente:

> *"Esta claro que si se elimina un terreno todas aquellas temporadas asociadas se eliminan, borrado en cascada, … **y si un terreno se elimina no quiero que afecte a la season**"*

Las dos frases dicen lo opuesto sobre el borrado de un terreno. Por el contexto (mencionas justo después la validación síncrona del **cultivo** vía gRPC y el resto de la frase distingue claramente "terreno" y "cultivo"), interpreto que **la segunda mención de "terreno" es un typo y debería ser "cultivo"**. Es decir:

- ✅ **Borrar un terreno** → cascada a las seasons (vía Kafka). Coherente con R4.
- ✅ **Borrar un cultivo** → **NO afecta** a las seasons. Coherente con R5.

Esto **además** es lo que **ya hace el código actual**, así que la interpretación es consistente con la realidad.

> **Si esta interpretación es incorrecta**, párate aquí y avisa. El resto del plan asume que es la correcta.

**Justificación de R5 (no propagar borrado de cultivo):** `crop` es un catálogo global. Las temporadas son **registros históricos** ("sembramos `Trigo blando` en este terreno en 2024"). Si un día borras el cultivo del catálogo, las temporadas no deberían desaparecer; conservan el `crop_id` aunque ya no resuelva contra una fila. Esto es exactamente el comportamiento que un sistema agrícola normativo (RD 1311/2012, Cuaderno de Explotación) exige.

---

## 4. Estado actual del código (verificado)

### 4.1 Esquema de BBDD — `V1__create_season_table.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE season_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);
INSERT INTO season_type (name) VALUES ('Planting'),('Harvest'),('Fallow'),('Dormancy');

CREATE TABLE season (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terrain_id UUID NOT NULL,                              -- ✅ R1, R2
    crop_id UUID NOT NULL,                                 -- ✅ R2, R3
    start_date DATE NOT NULL,
    end_date DATE,
    season_type_id INTEGER REFERENCES season_type(id),
    observations TEXT,
    CONSTRAINT start_before_end CHECK (end_date IS NULL OR end_date >= start_date)
);
```

✅ El esquema cubre R1, R2, R3 correctamente. **No hay** FK SQL hacia `terrain.id` ni `crop.id` (imposible cross-BBDD); la integridad la aporta gRPC + Kafka.

### 4.2 Endpoints REST (`SeasonController`)

| Método | Ruta | Estado |
|---|---|---|
| `GET` | `/season/{id}` | ✅ funciona |
| `GET` | `/season/terrain/{terrainId}` | ✅ funciona; ordena por `start_date DESC` |
| `POST` | `/season` | ✅ funciona; valida gRPC primero |
| `DELETE` | `/season/{id}` | ✅ funciona; devuelve 204 sin body |

### 4.3 Validación síncrona en POST /season — R6 ✅

`SeasonService.createSeason()` (`service/SeasonService.java:39-47`):

```java
@Transactional
public UUID createSeason(SeasonRequest request) {
    if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
        throw new IllegalArgumentException("Terrain with id " + request.terrain_id() + " does not exist");
    }
    if (!cropGrpcClient.checkCropExists(request.crop_id())) {
        throw new IllegalArgumentException("Crop with id " + request.crop_id() + " does not exist");
    }
    return seasonRepository.createSeason(request);
}
```

Ambos clientes gRPC están en `grpc/TerrainGrpcClient.java` y `grpc/CropGrpcClient.java`, anotados con `@GrpcClient` y stubs blocking.

### 4.4 Cascada por Kafka cuando se borra un terreno — R4 ✅

`listener/TerrainDeletedListener.java`:

```java
@KafkaListener(topics = "terrain-deleted", groupId = "season-service-group")
public void handleTerrainDeleted(TerrainDeletedEvent event) {
    log.info("Received TerrainDeletedEvent: {}", event);
    seasonService.deleteSeasonsByTerrainId(event.terrainId());
}
```

`SeasonService.deleteSeasonsByTerrainId(UUID)` ejecuta `DELETE FROM season WHERE terrain_id = ?`.

Configuración del consumer (`application.properties`):

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=season-service-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.value-deserializer=…ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=…JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.properties.spring.json.use.type.headers=false
spring.kafka.consumer.properties.spring.json.value.default.type=com.agro.seasonservice.event.TerrainDeletedEvent
```

El uso de `default.type` (en lugar de `type.mapping` recomendado por las reglas del proyecto §13.C) **funciona hoy** porque solo se consume un tópico, pero hay que migrarlo (gap M3 en §6).

### 4.5 No-cascada al borrar cultivo — R5 ✅

- `crop-service` **no tiene** `KafkaConfig` ni `EventPublisher` (verificado: no se publica ningún tópico).
- `season-service` **no tiene** ningún `@KafkaListener(topics="crop-deleted", …)`.
- Resultado: si se borra un crop, `season.crop_id` queda como referencia colgante; las filas de season **no** se tocan. **Es exactamente el comportamiento pedido.**

### 4.6 Tests existentes

```
src/test/java/com/agro/seasonservice/
├── SeasonServiceApplicationTests.java       # @SpringBootTest contextLoads
├── TestcontainersConfiguration.java         # PostgreSQL container
└── TestSeasonServiceApplication.java
```

Solo `contextLoads()`. Nada de unit/MockMvc/integration. Es **insuficiente** según `Microservice-creation-rules.md` §19 ("un servicio con solo `contextLoads()` no se considera terminado").

---

## 5. Contratos cross-servicio que season-service depende de

### 5.1 gRPC — terrain-service (`CheckTerrainExists`)

| Atributo | Valor |
|---|---|
| Servidor | `terrain-service:9093` (Docker) / `localhost:9093` (dev) |
| Proto | `terrain-service/src/main/proto/terrain.proto` (verificado idéntico al copiado en `season-service/src/main/proto/`) |
| Método | `CheckTerrainExists(TerrainIdRequest{terrain_id:string}) → TerrainExistsResponse{exists:bool}` |
| Comportamiento | UUID malformado → `exists=false` (no error). Verificado en `terrain-service/grpc/TerrainGrpcService.java`. |
| Documentación | `terrain-service/API_DOCUMENTATION.md` §6.1 |

✅ Sin cambios necesarios en terrain-service. Plan: solo verificar con un test de integración.

### 5.2 gRPC — crop-service (`CheckCropExists`)

| Atributo | Valor |
|---|---|
| Servidor | `crop-service:9094` (Docker) / `localhost:9094` (dev) |
| Proto | `crop-service/src/main/proto/crop.proto` (verificado idéntico al copiado en season-service) |
| Método | `CheckCropExists(CropIdRequest{crop_id:string}) → CropExistsResponse{exists:bool}` |
| Comportamiento | UUID malformado → `exists=false`. Verificado en `crop-service/grpc/CropGrpcService.java`. |
| Documentación | `crop-service/CROP_SERVICE_DOCUMENTACION.md` §5 |

✅ Sin cambios necesarios en crop-service.

### 5.3 Kafka — terrain-service (`terrain-deleted`)

| Atributo | Valor |
|---|---|
| Productor | `terrain-service.EventPublisher.publishTerrainDeleted()` |
| Topic | `terrain-deleted` (declarado en `terrain-service/config/KafkaConfig.java`) |
| Payload | `com.agro.terrainservice.event.TerrainDeletedEvent { UUID terrainId }` |
| Cuándo se publica | Al borrar un terreno (manual o saga RGPD desde `auth-service`); ver `terrain-service/API_DOCUMENTATION.md` §7.1 y §9.2 |
| Garantías | 1 partición, 1 réplica (suficiente para TFG) |

✅ El listener de season-service consume este topic correctamente. **No hay cambios** que tocar en terrain-service.

### 5.4 Kafka — crop-service (NO existe — confirmado)

`crop-service` NO publica eventos Kafka. Es la decisión arquitectónica que sostiene R5. Si en el futuro se quisiera implementar la "protección" de §11 de `CROP_SERVICE_DOCUMENTACION.md`, **NO** debería ser un `crop-deleted` cascade — debería ser un RPC `CheckCropInUse` consumido por crop-service antes de borrar (camino A documentado allí). Pero eso **NO entra en este plan**.

---

## 6. Gaps detectados (severidad ordenada)

> Cada gap tiene un ID estable `SS-<n>` (Season-Service-`n`) para referenciarlo en commits.

### 🔴 Crítico

#### SS-1. Typo en `application-prod.properties` rompe gRPC al crop-service en producción

**Archivo:** `season-service/src/main/resources/application-prod.properties`

```properties
grpc.client.crop-service.address=static://crop-se:9094     # ❌ "crop-se" es un host inexistente
```

**Debe ser:**

```properties
grpc.client.crop-service.address=static://crop-service:9094
```

**Impacto:** en Docker prod, todo `POST /season` que llegue al chequeo de cultivo lanza `RuntimeException("Failed to verify crop existence")` → 500 al cliente. **Bloquea la funcionalidad principal del servicio en prod.**

**Trabajo:** un fix de 1 línea. Mejor commitearlo aislado para que se pueda *cherry-pick* a hotfix branches.

---

### 🟠 Medio

#### SS-2. `IllegalArgumentException` de la validación gRPC se convierte en 500

**Archivo:** `season-service/src/main/java/com/agro/seasonservice/service/SeasonService.java`

Hoy:

```java
if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
    throw new IllegalArgumentException("Terrain with id " + request.terrain_id() + " does not exist");
}
```

Y `GlobalExceptionHandler` **no** tiene handler para `IllegalArgumentException` → cae en el handler default de Spring → 500.

**Cambio:** introducir excepciones custom y handlers diferenciados, en línea con cómo `terrain-service` y `crop-service` lo hacen tras los cierres recientes:

- Crear `exception/TerrainNotFoundException` (extends `RuntimeException`).
- Crear `exception/CropNotFoundException` (extends `RuntimeException`).
- Mapear ambas en `GlobalExceptionHandler` → 400 (o 404 si prefieres ser semántico) con `ProblemDetail` y title `"Terrain not found"` / `"Crop not found"`.

> **Por qué 400 y no 404:** el recurso pedido (`season`) no es lo que falta — es un dato del body el que apunta a una entidad inexistente. RFC 7231 considera 400 más apropiado para "el cliente envió datos referentes a algo que no existe en otro servicio". 404 también es defendible; alinear con la convención del repo que use `terrain-service`/`crop-service` (allí ambos usan 404 para "no existe").

#### SS-3. Mensajes de error hardcodeados en español en el service — viola §10 de las reglas

```java
throw new IllegalArgumentException("Terrain with id " + ... + " does not exist");
```

Las reglas del proyecto (`Microservice-creation-rules.md` §10 y §11) dicen: **ningún mensaje al cliente puede estar hardcodeado**.

**Cambio:** mover los textos al `I18nService` con claves `season.terrain.notfound`, `season.crop.notfound`, etc. (ver §7 paso 3).

#### SS-4. Dockerfile usa Alpine en build stage

**Archivo:** `season-service/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build       # ❌ musl libc rompe protoc-gen-grpc-java
```

Mismo bug que ya cerramos en `crop-service` (commit `de8bf76`). Hoy no falla porque las herramientas de build ya están en cache, pero el primer `docker build` desde cero en una máquina limpia fallará con `protoc-gen-grpc-java-1.63.0-linux-x86_64.exe: program not found`.

**Cambio:** `FROM eclipse-temurin:21-jdk-jammy AS build`. Una línea. Alineado con las reglas del proyecto §16 ("Build con JDK `jammy`; runtime con JRE `alpine`").

#### SS-5. Listener Kafka sin `@Transactional`

**Archivo:** `season-service/src/main/java/com/agro/seasonservice/listener/TerrainDeletedListener.java`

```java
@KafkaListener(topics = "terrain-deleted", groupId = "season-service-group")
public void handleTerrainDeleted(TerrainDeletedEvent event) {
    seasonService.deleteSeasonsByTerrainId(event.terrainId());
}
```

`deleteSeasonsByTerrainId` ya está anotado `@Transactional` en `SeasonService`. La cuestión es: **si el commit del consumer Kafka avanza el offset antes del commit de la transacción JDBC, podemos perder mensajes**. En la práctica con Spring Kafka el orden es: tx empieza → DELETE → tx commit → ack al broker, así que está OK por defecto. **Verificar** que `spring.kafka.consumer.enable-auto-commit` no esté en `true` (no aparece en properties → default es `false` o `true` según versión, **fijar explícitamente**):

```properties
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=RECORD
```

---

### 🟡 Bajo

#### SS-6. Refactor `spring.json.value.default.type` → `spring.json.type.mapping`

**Archivo:** `season-service/src/main/resources/application.properties`

Hoy:

```properties
spring.kafka.consumer.properties.spring.json.use.type.headers=false
spring.kafka.consumer.properties.spring.json.value.default.type=com.agro.seasonservice.event.TerrainDeletedEvent
```

Esto **fuerza** que cualquier mensaje Kafka entrante se deserialice como `TerrainDeletedEvent`. Funciona hoy porque solo hay un topic suscrito, pero si se añade otro (p. ej. el futuro `task-completed`) explotará.

**Cambio recomendado** (alineado con `terrain-service` que ya lo hace bien):

```properties
spring.kafka.consumer.properties.spring.json.use.type.headers=true
spring.kafka.consumer.properties.spring.json.type.mapping=\
  com.agro.terrainservice.event.TerrainDeletedEvent:com.agro.seasonservice.event.TerrainDeletedEvent
```

Así el deserializador resuelve el tipo correcto leyendo el header `__TypeId__` que pone el productor (Spring Kafka lo añade automáticamente).

#### SS-7. `Season` model usa `java.util.Date`

**Archivo:** `season-service/src/main/java/com/agro/seasonservice/model/Season.java`

```java
public record Season(UUID id, UUID terrain_id, UUID crop_id, Date start_date, ...) {}
```

`Date` está deprecado para fechas sin tiempo desde Java 8. El record `Season` no se usa hoy en respuestas (el repo usa `Map<String,Object>`), pero conviene alinear con `SeasonRequest` que ya usa `LocalDate`.

**Cambio:** sustituir `Date` por `LocalDate`. Riesgo cero (nada lo consume).

#### SS-8. `SeasonRequest` no valida `end_date >= start_date` en el DTO

Hoy esa validación existe **solo en BBDD** (`CONSTRAINT start_before_end CHECK (...)`). Si llega un body inválido, se intenta INSERT, Postgres lo rechaza, salta `DataIntegrityViolationException` → 500.

**Cambio:** añadir un `@AssertTrue` al record:

```java
public record SeasonRequest(
        @NotNull UUID terrain_id,
        @NotNull UUID crop_id,
        @NotNull LocalDate start_date,
        LocalDate end_date,
        Integer season_type_id,
        @Size(max = 2000) String observations
) {
    @AssertTrue(message = "{season.dates.range}")
    public boolean isEndAfterStart() {
        return end_date == null || !end_date.isBefore(start_date);
    }
}
```

#### SS-9. i18n bundles vacíos (solo 1 clave cada uno)

**Archivos:** `i18n/messages.properties` y `i18n/messages_es.properties`.

Actual:

```properties
season.not.found=Temporada no encontrada       # _es.properties
season.not.found=Season not found              # .properties
```

Tras SS-2, SS-3 y SS-8 hace falta añadir:

```properties
season.terrain.notfound       # del check gRPC
season.crop.notfound          # del check gRPC
season.dates.range            # de @AssertTrue
season.created                # mensaje 201 (opcional)
season.deleted                # mensaje 204 (opcional)
```

#### SS-10. Tests de profundidad insuficiente

Solo `contextLoads()`. Plan en §8.

---

## 7. Plan de implementación paso a paso

> Recomendación: **un commit por paso**. Cada commit deja el árbol compilable y los tests verdes. Mergear al final como un solo PR.

### Paso 1 — Hot-fix del typo en prod (SS-1)

**Archivo:** `season-service/src/main/resources/application-prod.properties`

```diff
-grpc.client.crop-service.address=static://crop-se:9094
+grpc.client.crop-service.address=static://crop-service:9094
```

**Commit:** `fix(season-service): hot-fix grpc client crop-se → crop-service in prod`

---

### Paso 2 — Excepciones custom + handlers diferenciados (SS-2, SS-3)

**Crear** `exception/TerrainNotFoundException.java`:

```java
package com.agro.seasonservice.exception;

public class TerrainNotFoundException extends RuntimeException {
    public TerrainNotFoundException(String message) { super(message); }
}
```

**Crear** `exception/CropNotFoundException.java`:

```java
package com.agro.seasonservice.exception;

public class CropNotFoundException extends RuntimeException {
    public CropNotFoundException(String message) { super(message); }
}
```

**Editar** `exception/GlobalExceptionHandler.java` para añadir:

```java
@ExceptionHandler(TerrainNotFoundException.class)
public ResponseEntity<ProblemDetail> handleTerrainNotFound(TerrainNotFoundException ex) {
    HttpStatus status = HttpStatus.NOT_FOUND;
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    pd.setTitle("Terrain not found");
    return ResponseEntity.status(status).body(pd);
}

@ExceptionHandler(CropNotFoundException.class)
public ResponseEntity<ProblemDetail> handleCropNotFound(CropNotFoundException ex) {
    HttpStatus status = HttpStatus.NOT_FOUND;
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    pd.setTitle("Crop not found");
    return ResponseEntity.status(status).body(pd);
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    pd.setTitle("Invalid argument");
    return ResponseEntity.status(status).body(pd);
}
```

**Editar** `service/SeasonService.java` para usar las excepciones nuevas + i18n:

```java
@Transactional
public UUID createSeason(SeasonRequest request) {
    if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
        throw new TerrainNotFoundException(
            i18nService.getMessage("season.terrain.notfound", request.terrain_id()));
    }
    if (!cropGrpcClient.checkCropExists(request.crop_id())) {
        throw new CropNotFoundException(
            i18nService.getMessage("season.crop.notfound", request.crop_id()));
    }
    return seasonRepository.createSeason(request);
}
```

**Inyectar** `I18nService` en `SeasonService` (añadir `private final I18nService i18nService;` al campo + import).

**Commit:** `feat(season-service): typed exceptions + 4xx mapping for grpc validation failures`

---

### Paso 3 — i18n (SS-9 + apoyo a SS-2, SS-3, SS-8)

**Archivo:** `season-service/src/main/resources/i18n/messages_es.properties`

```properties
season.not.found=La temporada no existe
season.terrain.notfound=El terreno con ID {0} no existe
season.crop.notfound=El cultivo con ID {0} no existe
season.dates.range=La fecha de fin no puede ser anterior a la fecha de inicio
season.created=Temporada creada
season.deleted=Temporada eliminada
season.terrain.required=El identificador del terreno es obligatorio
season.crop.required=El identificador del cultivo es obligatorio
season.start.required=La fecha de inicio es obligatoria
season.observations.size=Las observaciones no pueden superar 2000 caracteres
```

**Archivo:** `season-service/src/main/resources/i18n/messages.properties`

```properties
season.not.found=Season not found
season.terrain.notfound=Terrain with ID {0} does not exist
season.crop.notfound=Crop with ID {0} does not exist
season.dates.range=end_date must be on or after start_date
season.created=Season created
season.deleted=Season deleted
season.terrain.required=terrain id is required
season.crop.required=crop id is required
season.start.required=start date is required
season.observations.size=observations must not exceed 2000 characters
```

**Commit:** `i18n(season-service): fill out ES/EN bundles`

---

### Paso 4 — Validación de fechas en DTO (SS-8) + límite en observations

**Archivo:** `season-service/src/main/java/com/agro/seasonservice/dto/SeasonRequest.java`

Reescribir como:

```java
package com.agro.seasonservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonRequest(
        @NotNull(message = "{season.terrain.required}")
        UUID terrain_id,

        @NotNull(message = "{season.crop.required}")
        UUID crop_id,

        @NotNull(message = "{season.start.required}")
        LocalDate start_date,

        LocalDate end_date,

        Integer season_type_id,

        @Size(max = 2000, message = "{season.observations.size}")
        String observations
) {
    @AssertTrue(message = "{season.dates.range}")
    public boolean isEndAfterStart() {
        return end_date == null || !end_date.isBefore(start_date);
    }
}
```

**Commit:** `feat(season-service): @AssertTrue for date range + @Size on observations + i18n keys`

---

### Paso 5 — Refactor `Season` model (SS-7)

**Archivo:** `season-service/src/main/java/com/agro/seasonservice/model/Season.java`

```java
package com.agro.seasonservice.model;

import java.time.LocalDate;
import java.util.UUID;

public record Season(
        UUID id,
        UUID terrain_id,
        UUID crop_id,
        LocalDate start_date,
        LocalDate end_date,
        String observations,
        Integer season_type_id
) {
}
```

Riesgo cero — no hay nada que consuma este record hoy.

**Commit:** `refactor(season-service): Season uses LocalDate`

---

### Paso 6 — Kafka type-mapping y commit-mode (SS-5, SS-6)

**Archivo:** `season-service/src/main/resources/application.properties`

```diff
-spring.kafka.consumer.properties.spring.json.use.type.headers=false
-spring.kafka.consumer.properties.spring.json.value.default.type=com.agro.seasonservice.event.TerrainDeletedEvent
+spring.kafka.consumer.properties.spring.json.use.type.headers=true
+spring.kafka.consumer.properties.spring.json.type.mapping=\
+  com.agro.terrainservice.event.TerrainDeletedEvent:com.agro.seasonservice.event.TerrainDeletedEvent
+spring.kafka.consumer.enable-auto-commit=false
+spring.kafka.listener.ack-mode=RECORD
```

**Commit:** `chore(season-service): kafka type-mapping + manual commit ack-mode`

---

### Paso 7 — Dockerfile build stage a `jdk-jammy` (SS-4)

**Archivo:** `season-service/Dockerfile`

```diff
-FROM eclipse-temurin:21-jdk-alpine AS build
+FROM eclipse-temurin:21-jdk-jammy AS build
```

**Commit:** `fix(season-service): use jdk-jammy in build stage to fix protoc-gen-grpc on Alpine`

---

### Paso 8 — Tests (SS-10)

Ver §8.

---

## 8. Tests requeridos

> Mismo patrón que `terrain-service` y `crop-service`: unit (Mockito) + WebMvc slice + integration con Testcontainers para SQL real.

### 8.1 `SeasonServiceTest` (unit, Mockito)

**Casos mínimos:**

| Caso | Descripción |
|---|---|
| `createSeason_happyPath` | terrain y crop existen → INSERT y devuelve UUID |
| `createSeason_terrainNotFound_throwsTerrainNotFoundException` | gRPC `checkTerrainExists` devuelve `false` → no llama al repo |
| `createSeason_cropNotFound_throwsCropNotFoundException` | gRPC `checkCropExists` devuelve `false` → no llama al repo |
| `createSeason_terrainCheckFirst` | si ambas son falsas, se prueba primero terrain (orden de la cascada) |
| `deleteSeason_delegatesToRepository` | smoke |
| `deleteSeasonsByTerrainId_delegatesToRepository` | smoke |
| `getSeason_delegatesToRepositoryWithFormattedFields` | el `FieldsValidator` se invoca |

### 8.2 `SeasonControllerTest` (MockMvc standalone)

| Caso | Descripción |
|---|---|
| `createSeason_returns201WithUuid` | happy path |
| `createSeason_invalidBody_returns400WithErrors` | body vacío → 3+ errores |
| `createSeason_endBeforeStart_returns400` | el `@AssertTrue` lo rechaza |
| `createSeason_terrainNotFound_returns404` | service lanza `TerrainNotFoundException` → handler 404 |
| `createSeason_cropNotFound_returns404` | idem para crop |
| `getSeason_returnsMap` | repo mock |
| `getSeasonsByTerrain_returnsList` | repo mock |
| `deleteSeason_returns204NoBody` | smoke |
| `deleteSeason_unknownId_returns404` | service lanza `ResourceNotFoundException` |

### 8.3 `TerrainDeletedListenerTest` (unit + opcional `@EmbeddedKafka`)

| Caso | Descripción |
|---|---|
| `handleTerrainDeleted_callsDeleteByTerrainId` | mock del service, verificar invocación |
| (opcional) `endToEndKafka` | con `@EmbeddedKafka`, publicar mensaje y verificar que se llamó el listener |

### 8.4 `SeasonRepositoryIT` (Testcontainers PostgreSQL)

| Caso | Descripción |
|---|---|
| `createSeason_persistsAllFields` | fila aparece tras INSERT |
| `getSeason_returnsRow` | round-trip |
| `getSeasonsByTerrain_orderedByStartDateDesc` | verifica el `ORDER BY` |
| `deleteByTerrainId_removesAllSeasonsForThatTerrain` | la cascada funciona |
| `dbConstraint_startBeforeEnd_throws` | `end_date < start_date` → `DataIntegrityViolationException` |

### 8.5 Tests de integración cross-service (opcional, alta prioridad)

Con `@SpringBootTest` arrancando season-service y mocks gRPC para terrain/crop:

- `POST /season` con terrain mock devolviendo `exists=false` → 404 + ningún INSERT.
- `POST /season` con terrain y crop mock devolviendo true → 201.
- Topic `terrain-deleted` consumido (con `@EmbeddedKafka`) → seasons del terreno borradas.

### 8.6 Cobertura objetivo

JaCoCo ≥ **60 %** (línea base del proyecto). Ideal ≥ 75 % en `service/` y `controller/`.

---

## 9. Definition of Done

Antes de mergear `feat-season-service` a `main`:

- [ ] Los 7 commits del §7 aplicados, cada uno con tests verdes después de aplicarlo.
- [ ] `./mvnw -pl season-service -am verify` pasa desde la raíz.
- [ ] `docker compose up --build season-service crop-service terrain-service` arranca el stack.
- [ ] Smoke test manual: `curl POST /api/season` con `terrain_id` y `crop_id` válidos → 201.
- [ ] Smoke test manual: `curl POST /api/season` con `terrain_id` inexistente → 404 con `ProblemDetail{title:"Terrain not found"}`.
- [ ] Smoke test manual: `curl DELETE /api/terrain/{tid}` (publica `terrain-deleted`) → seasons del terreno desaparecen al cabo de < 1 s.
- [ ] Cobertura JaCoCo ≥ 60 % (verificada con `target/site/jacoco/index.html`).
- [ ] `project-guidelines.md` §4.5 actualizado si algún detalle del servicio cambia respecto a la documentación viva.
- [ ] **NO** hay fallback ni handler que devuelva 500 en operaciones que el cliente puede provocar con datos inválidos.

---

## 10. Fuera de alcance (delegado a otros paquetes)

> Estos puntos son importantes pero **no** entran en este plan. Documentados aquí para que nadie los confunda con omisiones.

| Tema | Por qué fuera | Dónde se aborda |
|---|---|---|
| Validación de **propiedad** del terreno (que `terrain.user_id == solicitante`) | Cambio cross-servicio (gateway + RPC `CheckTerrainOwnership`) | `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` §4-§6 + futuro Bloque A |
| HU-CUL-01: `variety`, `real_end_date`, `yield_*`, "cultivo activo único" | Migración nueva (V2), refactor del DTO, índice único parcial | `LLM-WORK/03-crop-season-service.md` §1 |
| HU-CUL-02: tabla `treatment` y endpoints anidados | Cambio grande de superficie | `LLM-WORK/03-crop-season-service.md` §2 |
| HU-CUL-03: filtros adicionales en GET, exportación CSV | Requiere completar otros paquetes primero | `LLM-WORK/03-crop-season-service.md` §3 |
| HU-CUL-04: cuaderno de explotación | Depende de HU-CUL-02/03 | `LLM-WORK/03-crop-season-service.md` §4 |
| Protección "cultivo en uso" antes de borrar un crop | Inversión de dependencia (crop-service → season-service) | `crop-service/CROP_SERVICE_DOCUMENTACION.md` §9.4 |
| Homogeneizar Spring Boot (3.4.0 actual vs 3.5.7 recomendado) | Project-wide | `project-guidelines.md` §8.2 |
| Activar `JwtValidation` en `/api/season/**` ya activo, pero propagar `X-User-Id` | Bloque A de la investigación | `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` §4.2 |

---

## 11. Mapa de archivos tocados

```
season-service/
├── Dockerfile                                                      (modify SS-4)
├── src/main/java/com/agro/seasonservice/
│   ├── controller/SeasonController.java                            (sin cambios — verifico)
│   ├── service/SeasonService.java                                  (modify SS-2, SS-3)
│   ├── repository/SeasonRepository.java                            (sin cambios)
│   ├── grpc/TerrainGrpcClient.java                                 (sin cambios)
│   ├── grpc/CropGrpcClient.java                                    (sin cambios)
│   ├── listener/TerrainDeletedListener.java                        (sin cambios)
│   ├── event/TerrainDeletedEvent.java                              (sin cambios)
│   ├── dto/SeasonRequest.java                                      (modify SS-8)
│   ├── model/Season.java                                           (modify SS-7)
│   ├── exception/GlobalExceptionHandler.java                       (modify SS-2)
│   ├── exception/TerrainNotFoundException.java                     (new)
│   └── exception/CropNotFoundException.java                        (new)
└── src/main/resources/
    ├── application.properties                                      (modify SS-5, SS-6)
    ├── application-prod.properties                                 (modify SS-1 — crítico)
    └── i18n/
        ├── messages.properties                                     (modify SS-9)
        └── messages_es.properties                                  (modify SS-9)

src/test/java/com/agro/seasonservice/
├── service/SeasonServiceTest.java                                  (new)
├── controller/SeasonControllerTest.java                            (new)
├── listener/TerrainDeletedListenerTest.java                        (new)
└── repository/SeasonRepositoryIT.java                              (new — Testcontainers)
```

**Total:** 2 archivos nuevos de excepción + 5 archivos modificados + 4 archivos nuevos de test = **11 archivos**. Cambio acotado.

---

> **Si aparece algún punto del §4 (estado actual) que no coincide con lo que ves en `main` cuando vayas a aterrizar, párate y avisa.** Este plan se construyó leyendo el código en `main` el 2026-05-10; si la rama base ha avanzado, la diff puede estar mal calibrada.
