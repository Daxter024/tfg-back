# Documentación de `crop-service`

> Documentación de referencia única del microservicio `crop-service`: contratos, esquema, flujos internos y **relación con el resto del sistema**.
>
> **Última actualización:** 2026-05-10 (commit `de8bf76` y posteriores)
> **Versión Spring Boot:** 4.0.0 · **Java:** 21
> **Estado de las deudas técnicas históricas:** la mayoría cerradas en esta iteración (ver §11).

---

## Índice

1. [Visión general y rol en el ecosistema](#1-visión-general-y-rol-en-el-ecosistema)
2. [Configuración y arranque](#2-configuración-y-arranque)
3. [Esquema de base de datos](#3-esquema-de-base-de-datos)
4. [Endpoints REST](#4-endpoints-rest)
5. [Servicio gRPC](#5-servicio-grpc)
6. [Validación, errores y status HTTP](#6-validación-errores-y-status-http)
7. [Internacionalización (i18n)](#7-internacionalización-i18n)
8. [Flujos de trabajo internos](#8-flujos-de-trabajo-internos)
9. [Relación con el resto de microservicios](#9-relación-con-el-resto-de-microservicios)
10. [Ejemplos `curl` listos para copiar](#10-ejemplos-curl-listos-para-copiar)
11. [Estado de la deuda técnica](#11-estado-de-la-deuda-técnica)
12. [Operación y troubleshooting](#12-operación-y-troubleshooting)

---

## 1. Visión general y rol en el ecosistema

`crop-service` es el **catálogo global de cultivos** del sistema agrícola. No tiene noción de "usuario propietario" — un cultivo definido aquí (p. ej. `Trigo blando`, `Manzana Reineta`, `Tomate raf`) puede ser referenciado por la temporada de cualquier agricultor.

**Responsabilidades:**

- CRUD del catálogo `crop` (`id, name, description, crop_type_id`).
- Lectura del catálogo cerrado `crop_type` (`CEREAL/FRUIT/VEGETABLE/TUBER/LEGUME`).
- Proyección dinámica de campos en respuesta vía `?fields=`.
- Filtro por tipo de cultivo vía `?crop_type_id=`.
- Validación gRPC de existencia de `crop` para servicios consumidores (principalmente `season-service`).

**Lo que NO hace y por qué:**

- **No emite ni consume eventos Kafka.** El catálogo es estable; los borrados son raros y administrados. Si en el futuro hay que limpiar referencias huérfanas en `season-service`, se publicará `crop-deleted` (ver §9.4 y §11).
- **No tiene autenticación propia.** La autorización vive en el `api-gateway` (hoy la ruta `/api/crop/**` es pública — ver §11).
- **No tiene `user_id` en el modelo.** El catálogo es global por diseño.
- **No expone CRUD de escritura para `crop_type` por API.** Los tipos son seeds del `V1` y se manipulan vía SQL si hace falta (raro).

**Posición en la arquitectura:**

```
Cliente HTTP ──> api-gateway:9000 ──/api/crop/**──> crop-service:8081 ──> crop-db:5432

                                                         ▲
                                                         │ gRPC :9094
                                                         │ CheckCropExists
                                                         │
                                                   season-service:8082
                                                   (antes de POST /season)
```

---

## 2. Configuración y arranque

### 2.1 Puertos

| Recurso | Puerto host | Puerto contenedor |
|---|---|---|
| HTTP REST | `8081` | `8081` |
| gRPC server | `9094` | `9094` |
| BBDD PostgreSQL (`crop-db`) | `5433` | `5432` |

### 2.2 Variables de entorno

| Variable | Default (perfil `dev`) | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` / `prod` |
| `SERVER_PORT` | `8081` | puerto HTTP del servicio |
| `DB_URL` | `jdbc:postgresql://localhost:5433/crop_db` | URL JDBC |
| `DB_USER` | `postgres` | usuario BBDD |
| `DB_PASSWORD` | `postgres` | clave BBDD |

En `prod` (Docker), `DB_URL` apunta a `crop-db:5432/crop_db` por la red interna `agro-net`.

### 2.3 Arranque

**Modo desarrollo (BBDD aparte, app local):**

```bash
docker compose up -d crop-db
./mvnw -pl crop-service spring-boot:run -Dspring-boot.run.profiles=dev
```

**Modo Docker completo (raíz del monorepo):**

```bash
docker compose up --build crop-service
```

**Modo Docker aislado (solo este servicio + su BBDD, en `agro-net`):**

```bash
# Asegurar que la red existe:
docker network create agro-net 2>/dev/null || true
cd crop-service && docker compose up --build
```

Flyway aplica `V1__create_crop_table.sql` automáticamente en el primer arranque (idempotente).

### 2.4 Imagen Docker

Multi-stage:

- **Build:** `eclipse-temurin:21-jdk-jammy` (Ubuntu/glibc — necesario porque `protoc-gen-grpc-java` está compilado contra glibc; Alpine rompía el build con "program not found" — ver §11 y §12.6).
- **Runtime:** `eclipse-temurin:21-jre-alpine` (imagen ligera).

---

## 3. Esquema de base de datos

Migración única hasta hoy: `crop-service/src/main/resources/db/migration/V1__create_crop_table.sql`.

### 3.1 Tabla `crop_type`

```sql
CREATE TABLE crop_type (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);
```

**Seeds insertados por la migración:**

| id | name |
|---|---|
| 1 | `CEREAL` |
| 2 | `FRUIT` |
| 3 | `VEGETABLE` |
| 4 | `TUBER` |
| 5 | `LEGUME` |

> Los IDs son `SERIAL`. Aunque hoy se asignan 1-5 por el orden del `INSERT`, **no los hardcodees** en clientes; siempre resuelve el id por nombre vía `GET /crop/type`.

### 3.2 Tabla `crop`

```sql
CREATE TABLE crop (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL,
    description  TEXT,
    crop_type_id INTEGER      REFERENCES crop_type(id)
);

CREATE INDEX idx_crop_name ON crop (name);
```

| Columna | Tipo SQL | Constraints | Notas |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | generado por Postgres en INSERT, devuelto al cliente vía `RETURNING id` |
| `name` | `VARCHAR(50)` | `NOT NULL` | indexado; el DTO `@Size(min=3, max=50)` está alineado |
| `description` | `TEXT` | nullable en BBDD, **obligatoria en API** (`@NotBlank` + `@Size(min=10, max=500)`) | |
| `crop_type_id` | `INTEGER` | FK → `crop_type(id)` (nullable en BBDD, **obligatorio en API**) | |

> **Nota sobre el mismatch BBDD ↔ API:** la BBDD acepta `description` y `crop_type_id` nulos por motivos históricos, pero el API los rechaza. Si insertas datos directamente por SQL y luego los lees por API, ten cuidado con los nulls.

---

## 4. Endpoints REST

Base path: `/crop` (sin prefijo `/api`; el `api-gateway` añade `/api` y aplica `StripPrefix=1`).

| Método | Ruta | Descripción | Status éxito |
|---|---|---|---|
| `GET` | `/crop` | Listar cultivos con proyección y filtro opcionales | 200 |
| `GET` | `/crop/type` | Listar tipos de cultivo | 200 |
| `POST` | `/crop` | Crear un cultivo | 201 |
| `DELETE` | `/crop/{id}` | Borrar un cultivo por id | 204 |

### 4.1 `GET /crop`

Lista todos los cultivos del catálogo. Soporta proyección dinámica de columnas y filtro por tipo.

**Query params:**

| Param | Tipo | Obligatorio | Default | Descripción |
|---|---|---|---|---|
| `fields` | string CSV | no | todas las columnas (`*`) | Lista de campos separados por coma a devolver. **Whitelist:** `id, name, description, crop_type_id`. Mayúsculas/minúsculas indistintas. |
| `crop_type_id` | integer | no | sin filtro | Si se indica, se valida contra `crop_type` y solo se devuelven cultivos de ese tipo. **Si el id no existe → 400 `Crop type not found`.** |

**Respuesta 200:** `application/json`. Array de objetos JSON con los campos solicitados (o todos si `fields` no se pasa). El orden de las claves no está garantizado.

**Errores:**

| Status | Causa |
|---|---|
| 400 | `fields` con un campo no permitido → `InvalidFieldException` (title `Campos invalido`) |
| 400 | `crop_type_id` no es un entero (binding) o no existe en `crop_type` (title `Crop type not found`) |

### 4.2 `GET /crop/type`

Devuelve la lista completa de tipos de cultivo.

**Sin parámetros.**

**Respuesta 200:** `application/json` con array de `{ id, name }`:

```json
[
  {"id": 1, "name": "CEREAL"},
  {"id": 2, "name": "FRUIT"},
  {"id": 3, "name": "VEGETABLE"},
  {"id": 4, "name": "TUBER"},
  {"id": 5, "name": "LEGUME"}
]
```

### 4.3 `POST /crop`

Crea un nuevo cultivo.

**Headers:** `Content-Type: application/json`.

**Body — `CropRequest`:**

| Campo | Tipo | Obligatorio | Validación | Mensaje i18n key |
|---|---|---|---|---|
| `name` | string | sí | `@NotBlank`, `@Size(min=3, max=50)` | `name.notblank`, `name.size` |
| `description` | string | sí | `@NotBlank`, `@Size(min=10, max=500)` | `description.notblank`, `description.size` |
| `crop_type_id` | integer | sí | `@NotNull`, `@Positive`; debe existir en `crop_type` | `croptype.id.notnull`, `croptype.id.negative`, `illegal.croptype.id` |

**Respuesta 201:** `application/json` con `CropCreatedResponse`:

```json
{
  "id": "8a4f3c8e-1234-4abc-9def-0123456789ab",
  "name": "Trigo blando",
  "message": "Cultivo creado"
}
```

> **Cambio reciente:** el endpoint **devolvía un string plano** (`"Cultivo creado"`) sin id. Tras el commit que cierra la deuda nº 1, ahora devuelve un objeto JSON con `id`, `name` y `message` traducido. El cliente puede usar el `id` directamente sin hacer un `GET` adicional.

**Errores:**

| Status | Causa | Body |
|---|---|---|
| 400 | Validación de campos | `ProblemDetail` con `errors: [...]` |
| 400 | `crop_type_id` inexistente | `ProblemDetail` con title `Crop type not found` |

### 4.4 `DELETE /crop/{id}`

Borra un cultivo por su UUID.

**Path param:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `id` | UUID | sí |

**Respuesta 204:** `No Content` **sin body** (semántica HTTP correcta).

**Errores:**

| Status | Causa | Title |
|---|---|---|
| 400 | `id` con formato incorrecto (no es UUID) | binding error |
| 404 | el id no existe en `crop` | `Crop not found` |

> **Cambio reciente:** antes esta ruta devolvía 204 con un body de mensaje (irregular HTTP) y un 400 con title incorrecto cuando el id no existía. Ahora devuelve 204 limpio o 404 con `ProblemDetail{title:"Crop not found"}`.

> **Importante (deuda nº 2 — abierta):** el borrado **no está protegido** contra cultivos en uso por una `season`. Si una `season` referencia este `crop_id`, esa fila quedará huérfana. Hasta que se implemente la protección cross-servicio (ver §9.4), **no borres `crop` en producción sin coordinación operacional**.

---

## 5. Servicio gRPC

`crop-service` expone un único RPC, consumido por `season-service` antes de insertar una `season`.

### 5.1 Contrato — `crop-service/src/main/proto/crop.proto`

```proto
syntax = "proto3";
package com.agro.crop.grpc;
option java_multiple_files = true;
option java_package = "com.agro.crop.grpc";

service CropService {
  rpc CheckCropExists (CropIdRequest) returns (CropExistsResponse);
}

message CropIdRequest  { string crop_id = 1; }
message CropExistsResponse { bool exists = 1; }
```

### 5.2 Comportamiento

- Recibe el `crop_id` como string (UUID).
- Si el string no es un UUID válido → devuelve `exists = false` (no es error gRPC).
- Si el UUID es válido → consulta `SELECT COUNT(*) FROM crop WHERE id = ?` y devuelve `exists = count > 0`.
- Read-only: **no muta nada**.
- Latencia esperada: < 5 ms en red Docker local.

### 5.3 Conexión desde clientes

| Entorno | Address |
|---|---|
| dev (local) | `static://localhost:9094` |
| prod (Docker) | `static://crop-service:9094` |

Negotiation: `plaintext`. Sin TLS (red interna privada).

> **Hot-fix conocido:** `season-service/src/main/resources/application-prod.properties` tenía `crop-se:9094` (typo). Verificar que esté como `crop-service:9094` antes de desplegar a prod.

### 5.4 Probar el RPC manualmente

```bash
grpcurl -plaintext localhost:9094 list
# com.agro.crop.grpc.CropService
# grpc.health.v1.Health
# grpc.reflection.v1alpha.ServerReflection

grpcurl -plaintext \
  -d '{"crop_id":"8a4f3c8e-1234-4abc-9def-0123456789ab"}' \
  localhost:9094 com.agro.crop.grpc.CropService/CheckCropExists
# {"exists": true}
```

---

## 6. Validación, errores y status HTTP

Todas las respuestas de error usan `ProblemDetail` (RFC 7807) con `Content-Type: application/problem+json`.

### 6.1 Excepciones gestionadas (`GlobalExceptionHandler`)

| Excepción | Status | Title | Origen típico |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `Campos invalido` | Bean Validation falla en `POST /crop` |
| `InvalidFieldException` | 400 | `Campos invalido` | `?fields=` con un campo fuera de la whitelist |
| `CropTypeNotFoundException` | 400 | `Crop type not found` | `crop_type_id` desconocido en POST o GET |
| `CropNotFoundException` | 404 | `Crop not found` | `id` de DELETE inexistente |
| `IllegalArgumentException` | 400 | `Invalid argument` | binding errors no cubiertos por las anteriores |
| `IntegrityViolationException` | 409 | `Integrity violation` | (no alcanzable hoy desde HTTP) |

### 6.2 Errores no gestionados

Cualquier `RuntimeException` no listada → handler default de Spring → 500 con `ProblemDetail` genérico. Los clientes deben tratar 5xx como transitorios y reintentar con backoff.

### 6.3 Whitelist de campos para `?fields=`

Definida en `constants/CropFieldConstants.java`:

```java
Set.of("id", "name", "description", "crop_type_id")
```

`fields` se parsea con `split(",")`, `trim()` y `toLowerCase()`. Cualquier otro nombre → 400.

> El whitelist es la **única** defensa contra inyección SQL en este servicio (la cláusula `SELECT` se construye por concatenación). **Nunca** añadir nuevas columnas al SELECT pasando estructuras dinámicas — siempre actualizar el set y validar.

---

## 7. Internacionalización (i18n)

Idiomas soportados: **español (`es`)** por defecto y **inglés (`en`)** como fallback para locales no traducidas.

El idioma se resuelve por la cabecera `Accept-Language` (estándar HTTP). Si no se envía, se usa `es` (configurado en `I18nConfig`).

> **Cómo funciona el fallback:** Spring `AcceptHeaderLocaleResolver` resuelve la **primera** locale del header. Si la locale no tiene bundle (p. ej. `zh`), `ResourceBundleMessageSource` cae al bundle base `messages.properties` (EN). **No** itera la cadena `Accept-Language` buscando bundles disponibles. Para un mejor matching habría que escribir un `LocaleResolver` custom — fuera de alcance hoy.

**Ficheros:**

- `src/main/resources/i18n/messages_es.properties`
- `src/main/resources/i18n/messages.properties` (EN, fallback)

**Claves (todas las traducciones rellenas tras la deuda nº 3):**

| Clave | ES | EN |
|---|---|---|
| `name.notblank` | El nombre es requerido | Name is required |
| `name.size` | El nombre debe tener entre 3 y 50 caracteres | The name must be between 3 and 50 characters |
| `description.notblank` | La descripción es requerida | Description is required |
| `description.size` | La descripción debe tener entre 10 y 500 caracteres | The description must be between 10 and 500 characters |
| `croptype.id.notnull` | El tipo de cultivo es requerido | The crop type is required |
| `croptype.id.negative` | El ID del tipo de cultivo debe ser positivo | The crop type ID must be positive |
| `illegal.croptype.id` | El tipo de cultivo no existe | Crop type does not exist |
| `illegal.argument.exception.title` | Argumento invalido | Invalid argument |
| `crop.created` | Cultivo creado | Crop created |
| `crop.notfound` | El cultivo con ID `{0}` no existe | Crop with ID `{0}` not found |
| `crop.integrity.exception` | "No se puede eliminar el tipo de cultivo. Existen cultivos asociados: `{0}`" | "Cannot delete crop type. Associated crops exist: `{0}`" |

---

## 8. Flujos de trabajo internos

### 8.1 Alta de un cultivo

```
Cliente HTTP                api-gateway              crop-service              crop-db
   │                           │                          │                       │
   │  POST /api/crop {…}       │                          │                       │
   ├──────────────────────────>│                          │                       │
   │                           │ StripPrefix=1            │                       │
   │                           │ → POST /crop {…}         │                       │
   │                           ├─────────────────────────>│                       │
   │                           │                          │ Bean Validation       │
   │                           │                          │ cropTypeExists?       │
   │                           │                          ├──────────────────────>│
   │                           │                          │<──── true ────────────┤
   │                           │                          │ INSERT … RETURNING id │
   │                           │                          ├──────────────────────>│
   │                           │                          │<──── id ──────────────┤
   │                           │<──── 201                 │                       │
   │                           │   {id, name, message}    │                       │
   │<───── 201 ────────────────┤                          │                       │
```

### 8.2 Listado filtrado por tipo de cultivo

```
season-service               crop-service               crop-db
       │                          │                        │
       │ GET /crop?crop_type_id=2 │                        │
       │ &fields=id,name          │                        │
       ├─────────────────────────>│                        │
       │                          │ FieldsValidator        │
       │                          │ cropTypeExists(2)      │
       │                          ├───────────────────────>│
       │                          │<──── true ─────────────┤
       │                          │ SELECT id,name FROM    │
       │                          │ crop WHERE             │
       │                          │ crop_type_id = 2       │
       │                          ├───────────────────────>│
       │                          │<───── rows ────────────┤
       │<──── 200 [{…}, {…}] ─────┤                        │
```

**Combinatoria de query strings:**

| Llamada | Resultado |
|---|---|
| `GET /crop` | Todos los cultivos, todas las columnas |
| `GET /crop?fields=id,name` | Todos, solo `id` y `name` |
| `GET /crop?crop_type_id=2` | Solo tipo `FRUIT`, todas las columnas |
| `GET /crop?fields=id&crop_type_id=2` | Solo tipo `FRUIT`, solo `id` |
| `GET /crop?crop_type_id=999` | **400** `Crop type not found` |
| `GET /crop?fields=secret` | **400** `Campos invalido` |
| `GET /crop?crop_type_id=abc` | **400** binding |

### 8.3 Borrado atómico

```
Cliente              crop-service                                  crop-db
   │                       │                                          │
   │ DELETE /api/crop/{id} │                                          │
   ├──────────────────────>│ DELETE FROM crop WHERE id = ?            │
   │                       │ (transacción única; sin SELECT previo)   │
   │                       ├─────────────────────────────────────────>│
   │                       │<──── rows = 1 ──────────────────────────┤
   │<───── 204 ────────────┤  (sin body)                              │
```

Si `rows == 0` → `CropNotFoundException` → 404. Esto elimina la ventana TOCTOU que tenía la versión previa (que hacía `cropExists()` + `DELETE` en operaciones separadas).

### 8.4 Lifecycle de la BBDD

```
docker compose up crop-db
   │
   ▼
Postgres arranca con db=crop_db
   │
   ▼
crop-service arranca
   │
   ▼
Flyway lee classpath:db/migration
   │
   ├── V1__create_crop_table.sql aplicado
   │       (crea crop_type, crop, idx_crop_name, seeds)
   │
   ▼
flyway_schema_history actualizado
   │
   ▼
Servicio escuchando :8081 (HTTP) y :9094 (gRPC)
```

**Si añades una nueva migración:** crea `V2__<descripcion>.sql` (nunca modifiques V1). Si una migración ya aplicada da problemas, crea `V3__fix_*.sql` y **no** uses `flyway repair` sin entender el efecto.

---

## 9. Relación con el resto de microservicios

Esta sección es la **fuente de verdad** sobre quién consume `crop-service` y de quién depende.

### 9.1 Mapa de dependencias

```
                       ┌─────────────┐
                       │ api-gateway │  reverse proxy + circuit breaker
                       │   :9000     │
                       └──────┬──────┘
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
          /auth/**       /api/crop/**   /api/season/**
                │             │             │
                ▼             ▼             ▼
        ┌───────────┐   ┌───────────┐   ┌────────────┐
        │   auth-   │   │   crop-   │   │  season-   │
        │ service   │   │  service  │◀──│  service   │  gRPC :9094
        │  :8083    │   │   :8081   │   │   :8082    │  CheckCropExists
        └─────┬─────┘   │ gRPC :9094│   └────────────┘  (antes de POST)
              │         └─────┬─────┘
              ▼               ▼
        ┌─────────┐     ┌─────────┐
        │ auth-db │     │ crop-db │
        │  :5435  │     │  :5433  │
        └─────────┘     └─────────┘
```

### 9.2 ¿Quién depende de `crop-service`?

| Quién | Cómo lo consume | Frecuencia | Crítico |
|---|---|---|---|
| `api-gateway` | Reverse proxy de `/api/crop/**` con `StripPrefix=1` y circuit breaker `cropService` | toda llamada externa | sí (si crop-service cae, el circuit breaker dispara fallback 503) |
| `season-service` (POST `/season`) | gRPC `CheckCropExists` antes de cada INSERT — para validar que el `crop_id` referenciado existe (suple la FK que no se puede declarar cross-BBDD) | una vez por POST de temporada | sí (si falla, no se crea la season) |
| `season-service` (HU-CUL-03, futuro Paquete 03) | REST `GET /crop?crop_type_id=X&fields=id` para resolver listas de `crop_id` por tipo y filtrar el histórico de temporadas | esporádica | no (degrada UX si falla) |

### 9.3 ¿De qué depende `crop-service`?

| Quién | Cómo | Notas |
|---|---|---|
| `crop-db` | JDBC directo, transaccional | sin él el servicio no arranca (Flyway no aplica) |

`crop-service` **no** depende de `auth-service`, `terrain-service` ni `season-service` para ninguna operación. Es un servicio **fuente** del catálogo.

### 9.4 Integridad referencial cross-BBDD

El sistema sigue *Database per Service*: no se pueden declarar FK cross-BBDD. Esto crea dos problemas de integridad sobre `crop`:

#### Problema 1 — INSERT en `season` con `crop_id` huérfano

**Solución actual (sincrónica, gRPC):** `season-service` llama a `CheckCropExists(crop_id)` **antes** del INSERT. Si `false`, aborta con error y nunca se crea la season. Esto sustituye al chequeo que haría una FK de Postgres.

#### Problema 2 — DELETE en `crop` deja `season.crop_id` huérfano

**Solución actual:** ❌ **no hay**. Si borras un `crop` con `season` referenciándolo, las filas de `season` quedan apuntando a un id inexistente. Es la deuda nº 2 (ver §11).

**Soluciones planificadas (no implementadas hoy):**

- **Camino A — sincrónico (recomendado):** `season-service` expone un nuevo RPC `CheckCropInUse(crop_id)`. `crop-service` lo llama **antes** del DELETE; si `in_use=true`, responde 409. Implica invertir parcialmente la dependencia (`crop-service` depende de `season-service` solo para integridad), aceptable porque la dependencia es de baja frecuencia.
- **Camino B — asíncrono (complementario):** `crop-service` publica `crop-deleted` en Kafka tras el DELETE. `season-service` consume y limpia / marca como deprecadas. **Limitación:** no protege contra orfandad, solo limpia post-mortem; eventualmente consistente.

El plan completo de la protección está documentado en `crop-service/crop-service-test-plan.md` §10 como bloque TDD (🚧).

### 9.5 Qué pasa si crop-service cae

| Operación afectada | Síntoma |
|---|---|
| `GET /api/crop` o `POST /api/crop` desde el frontend | El gateway abre el circuit breaker y devuelve 503 vía `/fallback/crop` |
| `season-service POST /season` | El RPC `CheckCropExists` lanza `StatusRuntimeException`. Hoy `season-service` lo traduce a `RuntimeException` → 500 hacia el cliente |
| Cualquier `GET /season/*` que enriquezca con `crop` | Sin impacto hoy (no se enriquece desde HU-CUL-03 hasta que se implemente) |

**Recomendación:** considerar añadir resilience4j en el cliente gRPC de `season-service` (retry + circuit breaker) cuando se aborde el Paquete 03.

---

## 10. Ejemplos `curl` listos para copiar

> Asumen que el `api-gateway` está en `localhost:9000`. Para llamar directo al servicio sustituye `http://localhost:9000/api/crop` por `http://localhost:8081/crop`.

### 10.1 Listar tipos de cultivo

```bash
curl -s http://localhost:9000/api/crop/type | jq .
```

### 10.2 Listar cultivos (todos, todas las columnas)

```bash
curl -s http://localhost:9000/api/crop | jq .
```

### 10.3 Listar cultivos con proyección y filtro

```bash
curl -s "http://localhost:9000/api/crop?fields=id,name&crop_type_id=2" | jq .
```

### 10.4 Crear un cultivo

```bash
curl -s -X POST http://localhost:9000/api/crop \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: es' \
  -d '{
    "name": "Trigo blando",
    "description": "Trigo de invierno apto para harina panificable",
    "crop_type_id": 1
  }'
# 201 Created
# {"id":"<uuid>","name":"Trigo blando","message":"Cultivo creado"}
```

**Caso de error (validación):**

```bash
curl -s -X POST http://localhost:9000/api/crop \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Tr",
    "description": "corto",
    "crop_type_id": -1
  }'
# 400 Bad Request
# {
#   "type":"about:blank","title":"Campos invalido","status":400,
#   "errors":[
#     "name: El nombre debe tener entre 3 y 50 caracteres",
#     "description: La descripción debe tener entre 10 y 500 caracteres",
#     "crop_type_id: El ID del tipo de cultivo debe ser positivo"
#   ]
# }
```

**Caso de error (`crop_type_id` inexistente):**

```bash
curl -s -X POST http://localhost:9000/api/crop \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","description":"...........","crop_type_id":999}'
# 400 Bad Request
# {"title":"Crop type not found","status":400,"detail":"El tipo de cultivo no existe"}
```

### 10.5 Borrar un cultivo

```bash
curl -s -X DELETE http://localhost:9000/api/crop/8a4f3c8e-1234-4abc-9def-0123456789ab -i
# HTTP/1.1 204 No Content
```

**Caso id inexistente:**

```bash
curl -s -X DELETE http://localhost:9000/api/crop/00000000-0000-0000-0000-000000000000 -i
# HTTP/1.1 404 Not Found
# Content-Type: application/problem+json
# {"title":"Crop not found","status":404,"detail":"El cultivo con ID 00000000-... no existe"}
```

### 10.6 Smoke test gRPC

```bash
grpcurl -plaintext \
  -d '{"crop_id":"8a4f3c8e-1234-4abc-9def-0123456789ab"}' \
  localhost:9094 com.agro.crop.grpc.CropService/CheckCropExists
# {"exists": true}
```

---

## 11. Estado de la deuda técnica

Comparativa **antes de esta iteración → estado actual**:

| # | Tema | Estado | Detalle |
|---|---|---|---|
| 1 | `POST /crop` no devolvía el id creado | ✅ **resuelto** | Devuelve `CropCreatedResponse{id, name, message}` (JSON 201). |
| 2 | `DELETE /crop/{id}` borra sin proteger consumidores | 🟡 **abierto, planificado** | Requiere coordinar con `season-service`. Plan documentado en §9.4 y en `crop-service-test-plan.md` §10. |
| 3 | `messages.properties` (EN) con claves vacías | ✅ **resuelto** | Todas las claves ES y EN rellenas. |
| 4 | Spring Boot 4.0.0 más nuevo que el resto | 🟡 **abierto, project-wide** | Decisión de homogeneización fuera del alcance de un solo paquete. |
| 5 | `/api/crop/**` sin `JwtValidation` en el gateway | 🟡 **abierto, fuera de scope** | Cambia el gateway, no este servicio. Activar cuando el modelo de roles del Paquete 03 esté listo. |
| 6 | `findAllCropDetails()` con SQL roto | ✅ **resuelto** | Eliminado el método junto con `CropDetailsDTO` y el row mapper (era código muerto). |
| 7 | `204 No Content` con body | ✅ **resuelto** | El controller ahora usa `ResponseEntity.noContent().build()`. |
| 8 | DTO `@Size(max=100)` vs columna `VARCHAR(50)` | ✅ **resuelto** | Bajado a `@Size(max=50)` (i18n actualizado). Alternativa "ampliar columna" descartada por no romper compatibilidad de datos. |
| 9 | DELETE TOCTOU (cropExists + DELETE no atómicos) | ✅ **resuelto** | Reemplazado por DELETE en una sentencia con `rows==0 → CropNotFoundException` → 404. |
| 10 | `IllegalArgumentException` con title incorrecto | ✅ **resuelto** | Diferenciadas `CropTypeNotFoundException` (400) y `CropNotFoundException` (404), cada una con su title. |

**Deuda restante (3 abiertas):** todas son de alcance project-wide o cross-servicio. Ninguna se cierra dentro de los límites de `crop-service` solo.

---

## 12. Operación y troubleshooting

### 12.1 Resetear la BBDD localmente (perder datos)

```bash
docker compose down -v
docker compose up crop-db
```

### 12.2 Forzar re-aplicación de migraciones sin afectar al resto

```bash
docker compose stop crop-service crop-db
docker volume rm tfg-back_db_crop_data
docker compose up -d crop-db
./mvnw -pl crop-service spring-boot:run
```

### 12.3 Conectarse a la BBDD para inspección

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d crop_db
crop_db=# \dt
crop_db=# SELECT * FROM crop_type;
crop_db=# SELECT id, name, crop_type_id FROM crop;
```

### 12.4 Ejecutar solo los tests del módulo

```bash
./mvnw -pl crop-service -am test
```

### 12.5 Ejecutar el plan de QA end-to-end

Con el servicio corriendo (`docker compose up crop-service crop-db`):

```bash
./crop-service/test-crop-plan.sh                  # todo
ONLY="1 2 4" ./crop-service/test-crop-plan.sh     # solo POST/GET/DELETE
SKIP_SLOW=1 ./crop-service/test-crop-plan.sh      # sin tests >1 MB
```

### 12.6 Build de la imagen Docker desde cero

```bash
docker build -f crop-service/Dockerfile -t crop-service crop-service/
```

> Si ves `PROTOC FAILED: protoc-gen-grpc-java-1.63.0-linux-x86_64.exe: program not found`, comprueba que el stage de build usa `eclipse-temurin:21-jdk-jammy`, no `:21-jdk-alpine`. Alpine usa musl libc, incompatible con el binario glibc del plugin gRPC.

### 12.7 Observabilidad

- **Logs:** stdout en formato Spring por defecto. Flyway loggea a nivel `INFO`.
- **Health:** no hay actuator configurado. Para añadirlo, sumar `spring-boot-starter-actuator` al `pom.xml` y exponer `/actuator/health` (cambio cross-cutting con todos los servicios — no se aborda aquí).
- **Métricas:** no expuestas hoy.

---

> Si esta documentación deja de coincidir con el código, **manda un PR** con la corrección. La fuente de verdad es siempre el código + esta documentación + `LLM-WORK/03-crop-season-service.md` para las decisiones de diseño cross-servicio.
