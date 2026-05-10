# Guía de `crop-service`

> Documentación de referencia para programadores que **consumen** o **mantienen** el microservicio `crop-service` tras la implementación del filtro `crop_type_id` (Paquete 03 §3.1, commit `7840e43`).
>
> **Última actualización:** 2026-05-10
> **Versión Spring Boot:** 4.0.0 · **Java:** 21

---

## Índice

1. [Visión general](#1-visión-general)
2. [Configuración y arranque](#2-configuración-y-arranque)
3. [Esquema de base de datos](#3-esquema-de-base-de-datos)
4. [Endpoints REST](#4-endpoints-rest)
5. [Servicio gRPC](#5-servicio-grpc)
6. [Validación, errores y status HTTP](#6-validación-errores-y-status-http)
7. [Internacionalización (i18n)](#7-internacionalización-i18n)
8. [Flujos de trabajo](#8-flujos-de-trabajo)
9. [Ejemplos `curl` listos para copiar](#9-ejemplos-curl-listos-para-copiar)
10. [Notas operativas](#10-notas-operativas)

---

## 1. Visión general

`crop-service` es un microservicio Spring Boot que actúa como **catálogo global de cultivos** del sistema. No tiene noción de "usuario propietario" — es un dominio compartido (un cultivo definido aquí puede ser usado por cualquier `season` de cualquier agricultor).

**Responsabilidades:**

- CRUD básico sobre `crop` (cultivos): nombre, descripción, tipo.
- Lectura de `crop_type` (tipos enumerados: `CEREAL`, `FRUIT`, `VEGETABLE`, `TUBER`, `LEGUME`).
- Filtrado dinámico de campos en respuesta vía `?fields=`.
- Filtrado por tipo de cultivo vía `?crop_type_id=` (**nuevo**).
- Validación de existencia de `crop` por gRPC (consumido principalmente por `season-service`).

**Lo que NO hace:**

- No emite ni consume eventos Kafka.
- No tiene autenticación propia (la ruta `/api/crop/**` del gateway hoy no aplica filtro JWT — ver §10).
- No tiene `user_id` en su modelo: el catálogo es global.

---

## 2. Configuración y arranque

### 2.1 Puertos

| Recurso | Puerto |
|---|---|
| HTTP REST | `8081` |
| gRPC server | `9094` |
| BBDD PostgreSQL (`crop-db`) | `5433` (host) → `5432` (contenedor) |

### 2.2 Variables de entorno

| Variable | Default (perfil `dev`) | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` / `prod` |
| `SERVER_PORT` | `8081` | puerto HTTP del servicio |
| `DB_URL` | `jdbc:postgresql://localhost:5433/crop_db` | URL JDBC |
| `DB_USER` | `postgres` | usuario BBDD |
| `DB_PASSWORD` | `postgres` | clave BBDD |

En `prod` (Docker), `DB_URL` debe apuntar a `crop-db:5432/crop_db` (red interna).

### 2.3 Arranque

**Modo desarrollo (con la BBDD ya levantada):**

```bash
docker compose up -d crop-db
./mvnw -pl crop-service spring-boot:run -Dspring-boot.run.profiles=dev
```

**Modo Docker completo:**

```bash
docker compose up --build crop-service
```

Flyway aplica `V1__create_crop_table.sql` automáticamente al primer arranque.

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

> Los IDs son `SERIAL`, así que dependen del orden de inserción. **No los hardcodees** en cliente; siempre resuelve el id por nombre vía `GET /crop/type` o tras inserción.

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
| `id` | `UUID` | PK, default `gen_random_uuid()` | generado por Postgres |
| `name` | `VARCHAR(50)` | `NOT NULL` | indexado |
| `description` | `TEXT` | nullable en BBDD, **obligatorio en API** (ver §4.4) | |
| `crop_type_id` | `INTEGER` | FK a `crop_type(id)` (nullable a nivel BBDD, **obligatorio en API**) | |

**Mismatch BBDD ↔ API a tener en cuenta:** la BBDD permite `description` y `crop_type_id` nulos, pero el DTO `CropRequest` los rechaza con `@NotBlank` / `@NotNull`. Si insertas datos directamente por SQL ten cuidado con los nulls; el API siempre exigirá ambos.

---

## 4. Endpoints REST

Base path: `/crop` (sin prefijo `/api`; el `api-gateway` añade `/api` y aplica `StripPrefix=1`).

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/crop` | Listar cultivos con proyección y filtro opcionales | pública (gateway) |
| `GET` | `/crop/type` | Listar tipos de cultivo | pública |
| `POST` | `/crop` | Crear un cultivo | pública |
| `DELETE` | `/crop/{id}` | Borrar un cultivo por id | pública |

> **Importante:** "pública" aquí significa que el gateway **no** aplica `JwtValidation` a `/api/crop/**` hoy (§10 lista esto como deuda de seguridad). El servicio en sí no autentica.

### 4.1 `GET /crop`

Lista todos los cultivos del catálogo. Soporta proyección dinámica de columnas y filtro por tipo.

**Query params:**

| Param | Tipo | Obligatorio | Default | Descripción |
|---|---|---|---|---|
| `fields` | string CSV | no | todas las columnas (`*`) | Lista de campos separados por coma a devolver. **Whitelist:** `id, name, description, crop_type_id`. Mayúsculas/minúsculas indistintas. |
| `crop_type_id` | integer | no | sin filtro | Si se indica, se valida contra `crop_type` y solo se devuelven cultivos de ese tipo. **Si el id no existe → 400.** |

**Respuesta 200:** `application/json`. Lista de objetos JSON con los campos solicitados (o todos si `fields` no se pasa). El orden de las claves no está garantizado.

**Errores:**

| Status | Causa |
|---|---|
| 400 | `fields` contiene un campo no permitido → `InvalidFieldException`. |
| 400 | `crop_type_id` no es un entero (binding) o no existe en `crop_type`. |

**Ejemplo de payload:**

```json
[
  {
    "id": "8a4f3c8e-...",
    "name": "Trigo blando",
    "description": "Trigo de invierno apto para harina panificable",
    "crop_type_id": 1
  },
  ...
]
```

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
| `name` | string | sí | `@NotBlank`, `@Size(min=3, max=100)` | `name.notblank`, `name.size` |
| `description` | string | sí | `@NotBlank`, `@Size(min=10, max=500)` | `description.notblank`, `description.size` |
| `crop_type_id` | integer | sí | `@NotNull`, `@Positive`; debe existir en `crop_type` | `croptype.id.notnull`, `croptype.id.negative`, `illegal.croptype.id` |

**Respuesta 201:** `text/plain` (sí, `String`) con el mensaje i18n `crop.created` (p. ej. `"Cultivo creado"` en `es`).

**Errores:**

| Status | Causa | Body |
|---|---|---|
| 400 | Validación de campos | `ProblemDetail` con `errors: [...]` |
| 400 | `crop_type_id` inexistente | `ProblemDetail` con título `"No existe ese tipo de cultivo"` y mensaje `illegal.croptype.id` |

> **Limitación actual:** el endpoint **no devuelve el `id` del cultivo creado** ni el objeto entero, solo un mensaje. Si el cliente lo necesita debe hacer `GET /crop?fields=id,name` y filtrar — está en deuda técnica (§10).

### 4.4 `DELETE /crop/{id}`

Borra un cultivo por su UUID.

**Path param:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `id` | UUID | sí |

**Respuesta 204:** `No Content` con un body de mensaje i18n `crop.deleted` (técnicamente irregular: 204 no debería llevar body; los clientes deben ignorarlo).

**Errores:**

| Status | Causa |
|---|---|
| 400 | `id` con formato incorrecto (binding) |
| 400 | `id` no existe → `IllegalArgumentException("crop not found")` |

> **No** se hace borrado lógico (soft-delete) ni se valida si hay `season` referenciando este `crop`. El borrado es físico e inmediato. Si una `season` apunta a un `crop` borrado, la fila de `season` quedará huérfana (no hay FK cross-BBDD que lo impida — ver §8.4).

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
- Si el string no es un UUID válido → devuelve `exists = false` (no error).
- Si el UUID es válido → consulta `SELECT COUNT(*) FROM crop WHERE id = ?` y devuelve `exists = count > 0`.

### 5.3 Conexión

| Entorno | Address |
|---|---|
| dev (local) | `static://localhost:9094` |
| prod (Docker) | `static://crop-service:9094` |

Negotiation: `plaintext`. Sin TLS.

> **Hot-fix conocido:** `season-service/src/main/resources/application-prod.properties` tenía `crop-se:9094` (typo). Verificar que esté como `crop-service:9094` antes de desplegar a prod.

---

## 6. Validación, errores y status HTTP

Todas las respuestas de error usan `ProblemDetail` (RFC 7807) con `Content-Type: application/problem+json`.

### 6.1 Excepciones gestionadas (`GlobalExceptionHandler`)

| Excepción | Status | Título del `ProblemDetail` | Origen típico |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `Campos invalido` | Bean Validation falla en `POST /crop` |
| `InvalidFieldException` | 400 | `Campos invalido` | `?fields=` con un campo fuera de la whitelist |
| `IllegalArgumentException` | 400 | `No existe ese tipo de cultivo` | `crop_type_id` desconocido o `id` de borrado inexistente |
| `IntegrityViolationException` | 409 | `Integrity violation` | (no alcanzable por endpoints HTTP actuales — solo si se invoca `deleteCropType` programáticamente) |

### 6.2 Errores no gestionados explícitamente

Cualquier `RuntimeException` no listada caerá en el handler default de Spring → 500 con `ProblemDetail` genérico. **Recomendado:** los clientes deben tratar 5xx como transitorios y reintentar (con backoff).

### 6.3 Whitelist de campos para `?fields=`

Definida en `constants/CropFieldConstants.java`:

```java
Set.of("id", "name", "description", "crop_type_id")
```

`fields` se parsea con `split(",")`, `trim()` y `toLowerCase()`. Cualquier otro nombre → 400.

> El whitelist es la **única** defensa contra inyección SQL en este servicio (la cláusula `SELECT` se construye por concatenación). **Nunca** añadir nuevas columnas al SELECT pasando estructuras dinámicas — siempre actualizar el set y validar.

---

## 7. Internacionalización (i18n)

Idiomas soportados: **español (`es`)** por defecto y **inglés (`en`)** como fallback.

El idioma se resuelve por la cabecera `Accept-Language` (estándar HTTP). Si no se envía, se usa `es`.

**Ficheros:**

- `src/main/resources/i18n/messages_es.properties`
- `src/main/resources/i18n/messages.properties` (fallback en inglés — algunas claves están vacías; ver §10)

**Claves principales (no exhaustivo):**

| Clave | ES | EN |
|---|---|---|
| `name.notblank` | El nombre es requerido | Name is required |
| `name.size` | El nombre debe tener entre 3 y 100 caracteres | The name must be between 3 and 100 characters |
| `description.notblank` | La descripción es requerida | Description is required |
| `description.size` | La descripción debe tener entre 10 y 500 caracteres | The description must be between 10 and 500 characters |
| `croptype.id.notnull` | El tipo de cultivo es requerido | The crop type is required |
| `croptype.id.negative` | El ID del tipo de cultivo debe ser positivo | The crop type ID must be positive |
| `illegal.croptype.id` | El tipo de cultivo no existe | *(vacía hoy)* |
| `crop.created` | Cultivo creado | Crop created |
| `crop.deleted` | El cultivo se ha eliminado | Crop has been deleted |
| `crop.integrity.exception` | No se puede eliminar el tipo de cultivo. Existen cultivos asociados: `{0}` | Cannot delete crop type. Associated crops exist: `{0}` |

---

## 8. Flujos de trabajo

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
   │                           │                          │ INSERT INTO crop      │
   │                           │                          ├──────────────────────>│
   │                           │                          │<──── 1 row ───────────┤
   │                           │<──── 201 "Cultivo creado"┤                       │
   │<───── 201 ────────────────┤                          │                       │
```

**Pasos del cliente:**

1. (opcional) `GET /api/crop/type` para resolver el `id` del `crop_type` deseado.
2. `POST /api/crop` con `{ name, description, crop_type_id }`.
3. Manejar 400 (validación) y 201 (éxito). El cliente que necesite el `id` recién creado debe hacer un `GET` adicional filtrando por `name` (deuda — §10).

### 8.2 Listado filtrado por tipo de cultivo (caso de uso del Paquete 03)

Es el flujo nuevo introducido en este paquete. Lo consumirá `season-service` para implementar el filtro `?crop_type_id=X` en el histórico de temporadas (HU-CUL-03).

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
       │                          │                        │
       │ Construye lista de       │                        │
       │ crop_id y filtra         │                        │
       │ season WHERE             │                        │
       │ crop_id = ANY(...)       │                        │
```

**Combinaciones soportadas del query string:**

| Llamada | Comportamiento |
|---|---|
| `GET /crop` | Todos los cultivos, todas las columnas |
| `GET /crop?fields=id,name` | Todos los cultivos, solo `id` y `name` |
| `GET /crop?crop_type_id=2` | Solo cultivos de tipo `FRUIT`, todas las columnas |
| `GET /crop?fields=id&crop_type_id=2` | Solo cultivos de tipo `FRUIT`, solo `id` |
| `GET /crop?crop_type_id=999` | **400** — tipo inexistente |
| `GET /crop?fields=secret` | **400** — campo no permitido |
| `GET /crop?crop_type_id=abc` | **400** — binding falla |

### 8.3 Validación remota desde `season-service` (gRPC)

Este flujo se ejecuta automáticamente cada vez que un cliente envía `POST /api/season`:

```
season-service               crop-service (gRPC :9094)         crop-db
       │                              │                            │
       │ CheckCropExists(crop_id)     │                            │
       ├─────────────────────────────>│                            │
       │                              │ UUID.fromString → ok?      │
       │                              │ SELECT COUNT(*) FROM crop  │
       │                              ├───────────────────────────>│
       │                              │<──── count ────────────────┤
       │<──── { exists: bool } ───────┤                            │
       │                              │                            │
       │ Si exists=false → abort      │
       │ con IllegalArgumentException │
```

**Garantías:**

- El RPC es **read-only**: no muta nada.
- Si `crop_id` no es un UUID válido → `exists = false` (no error gRPC).
- Latencia esperada: < 5 ms en red local de Docker.

### 8.4 Borrado de un cultivo en uso

Hoy **no hay protección**. La secuencia es:

```
DELETE /api/crop/{id}  →  204
   │
   └── season filas con crop_id = X quedan huérfanas (referencia rota)
       (no hay Kafka topic `crop-deleted` ni FK cross-BBDD)
```

Si en el futuro se quiere proteger:

- **Opción A:** publicar `crop-deleted` y que `season-service` reaccione (eliminando o marcando como deprecadas).
- **Opción B:** rechazar el DELETE si algún `season` lo referencia (requiere RPC inverso `CheckCropInUse` en `season-service`, lo que invierte la dependencia — antipatrón).

Está documentado como deuda en §10. **Mientras tanto, no borres `crop` que estén en uso.**

### 8.5 Lifecycle de la BBDD

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

## 9. Ejemplos `curl` listos para copiar

> Asumen que el `api-gateway` está en `localhost:9000`. Para llamar directo al servicio sustituye `http://localhost:9000/api/crop` por `http://localhost:8081/crop`.

### 9.1 Listar tipos de cultivo

```bash
curl -s http://localhost:9000/api/crop/type | jq .
```

### 9.2 Listar cultivos (todos, todas las columnas)

```bash
curl -s http://localhost:9000/api/crop | jq .
```

### 9.3 Listar cultivos con proyección y filtro

```bash
curl -s "http://localhost:9000/api/crop?fields=id,name&crop_type_id=2" | jq .
```

### 9.4 Crear un cultivo

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
# "Cultivo creado"
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
#   "errors":["name: El nombre debe tener entre 3 y 100 caracteres",
#             "description: La descripción debe tener entre 10 y 500 caracteres",
#             "crop_type_id: El ID del tipo de cultivo debe ser positivo"]
# }
```

**Caso de error (`crop_type_id` inexistente):**

```bash
curl -s -X POST http://localhost:9000/api/crop \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","description":"...........","crop_type_id":999}'
# 400 Bad Request
# {"title":"No existe ese tipo de cultivo","status":400,"detail":"El tipo de cultivo no existe"}
```

### 9.5 Borrar un cultivo

```bash
curl -s -X DELETE http://localhost:9000/api/crop/8a4f3c8e-1234-4abc-9def-0123456789ab -i
# HTTP/1.1 204 No Content
```

### 9.6 Probar el RPC gRPC manualmente (con `grpcurl`)

```bash
# Listar servicios gRPC expuestos
grpcurl -plaintext localhost:9094 list

# Llamada
grpcurl -plaintext \
  -d '{"crop_id":"8a4f3c8e-1234-4abc-9def-0123456789ab"}' \
  localhost:9094 com.agro.crop.grpc.CropService/CheckCropExists
# {"exists": true}
```

---

## 10. Notas operativas

### 10.1 Deuda técnica conocida

| # | Tema | Impacto | Plan |
|---|---|---|---|
| 1 | `POST /crop` no devuelve el id creado | UX: cliente debe hacer un GET adicional | Cambiar respuesta a `{ "id": UUID, "message": "..." }`. Romper retrocompatibilidad si es necesario. |
| 2 | `DELETE /crop/{id}` borra físicamente sin avisar a consumidores | `season` queda con `crop_id` huérfano | Publicar evento Kafka `crop-deleted`; `season-service` consume y reacciona. |
| 3 | `messages.properties` (EN) tiene varias claves vacías (p. ej. `illegal.croptype.id=""`) | Errores 400 sin mensaje en clientes con `Accept-Language: en` | Rellenar los strings ingleses. |
| 4 | Spring Boot 4.0.0 — versión más nueva que el resto del monorepo | Posibles incompatibilidades con dependencias añadidas en otros paquetes | Convergencia futura a la versión común. |
| 5 | Ruta `/api/crop/**` en el gateway no aplica `JwtValidation` | Cualquiera puede crear/borrar cultivos | Añadir filtro JWT cuando el modelo de roles lo soporte (HU-CUL-* puede requerir solo lectura pública y escritura admin). |
| 6 | `findAllCropDetails()` en `CropRepository` tiene SQL roto (falta una coma) | Método unreachable hoy, pero compila | Arreglar antes de exponerlo. |
| 7 | `204 No Content` con body | Algunos clientes/proxies lo descartan | Devolver 200 + body o 204 sin body. |

### 10.2 Operaciones frecuentes

**Resetear la BBDD localmente** (perder datos):

```bash
docker compose down -v
docker compose up crop-db
```

**Forzar re-aplicación de migraciones en local (sin perder otras BBDD):**

```bash
docker compose stop crop-service crop-db
docker volume rm tfg-back_db_crop_data
docker compose up -d crop-db
./mvnw -pl crop-service spring-boot:run
```

**Conectarse a la BBDD para inspección:**

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d crop_db
crop_db=# \dt
crop_db=# SELECT * FROM crop_type;
```

**Ejecutar solo los tests del módulo:**

```bash
./mvnw -pl crop-service -am test
```

### 10.3 Observabilidad

- **Logs:** stdout en formato Spring por defecto. Flyway loggea a nivel `INFO` (`logging.level.org.flywaydb=INFO`).
- **Health:** no hay actuator configurado en este servicio (a diferencia de `api-gateway`). Si lo necesitas, añadir `spring-boot-starter-actuator` al `pom.xml`.
- **Métricas:** no expuestas hoy.

### 10.4 Convivencia con otros servicios

| Quién | Cómo lo consume |
|---|---|
| `season-service` (POST /season) | gRPC `CheckCropExists` antes de cada INSERT (§8.3). |
| `season-service` (HU-CUL-03 — Paquete 03) | REST `GET /crop?crop_type_id=X&fields=id` para resolver listas de `crop_id` por tipo. |
| `api-gateway` | Reverse proxy de `/api/crop/**` con `StripPrefix=1` y circuit breaker `cropService`. |

---

> Si esta guía deja de coincidir con el código, **manda un PR** con la corrección. La fuente de verdad es siempre el código + `LLM-WORK/03-crop-season-service.md` para las decisiones de diseño.
