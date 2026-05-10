# `crop-service` — Plan de tests exhaustivo

> Documento de referencia para que un desarrollador (humano o agente IA) pueda **implementar** y **verificar** todos los casos de comportamiento del microservicio `crop-service` en su estado actual y en su estado objetivo (tras cerrar la deuda de §10 de `CROP_SERVICE_GUIDE.md`, especialmente la **protección contra borrado de cultivos en uso**).
>
> Cada caso lleva un identificador estable `CROP-XX.YY` que el desarrollador puede usar como nombre del método de test (`@DisplayName`) y como referencia en commits/PRs.
>
> Para cada caso se indica:
> - **Cómo provocarlo** (request HTTP, llamada gRPC, side-effect).
> - **Qué debe devolver el servicio** (status, body, mensaje i18n).
> - **Qué debe quedar persistido o emitido** (cuando aplica).
> - **Capa de test recomendada** (unit / WebMvc / JDBC / integración / gRPC).
> - **Estado** del caso: ✅ ya cubierto · 🟡 cubierto parcialmente · ❌ no cubierto · 🚧 requiere implementación previa de funcionalidad.

---

## Índice

- [0. Convenciones y plantilla común](#0-convenciones-y-plantilla-común)
- [1. Tests de `POST /crop` — alta de cultivo](#1-tests-de-post-crop--alta-de-cultivo)
- [2. Tests de `GET /crop` — listado y proyección](#2-tests-de-get-crop--listado-y-proyección)
- [3. Tests de `GET /crop/type` — catálogo de tipos](#3-tests-de-get-croptype--catálogo-de-tipos)
- [4. Tests de `DELETE /crop/{id}` — borrado seguro](#4-tests-de-delete-cropid--borrado-seguro)
- [5. Tests de gRPC `CheckCropExists`](#5-tests-de-grpc-checkcropexists)
- [6. Tests del repositorio (BBDD real con Testcontainers)](#6-tests-del-repositorio-bbdd-real-con-testcontainers)
- [7. Tests transversales (i18n, ProblemDetail, headers, locales)](#7-tests-transversales-i18n-problemdetail-headers-locales)
- [8. Tests de seguridad (defensivos)](#8-tests-de-seguridad-defensivos)
- [9. Tests del filtro `crop_type_id` (Paquete 03)](#9-tests-del-filtro-crop_type_id-paquete-03)
- [10. Tests de la protección "cultivo en uso" (NUEVO — bloque 🚧)](#10-tests-de-la-protección-cultivo-en-uso-nuevo--bloque-)
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
| **JDBC slice** | `@JdbcTest + @Import(CropRepository.class)` con Testcontainers PostgreSQL | SQL real, mapeos, constraints. Nada de servlets. |
| **Integración full** | `@SpringBootTest(webEnvironment=RANDOM_PORT) + @Testcontainers` con `postgres:14` | Flyway + JdbcTemplate + gRPC server arrancado. Para gRPC end-to-end y para reproducir el flujo cross-service con WireMock. |
| **gRPC test** | `InProcessServer` o `@SpringBootTest` con `grpc.server.in-process-name` | RPC `CheckCropExists` exposed. |
| **Contract test (consumer-driven)** | `Spring Cloud Contract` o WireMock + golden JSON | Verifica que el `crop-service` cumple la forma que `season-service` espera en `GET /crop?crop_type_id=X`. |

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

- En **unit tests**, mockear `I18nService` para que devuelva la propia clave: `when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));`. Asserts contra claves (`crop.created`), no contra texto traducido.
- En **WebMvc/integración**, alternar `Accept-Language: es` y `Accept-Language: en` y verificar que el `LocaleResolver` resuelve correctamente.

### 0.4 Estado inicial supuesto

Antes de cada test (donde aplique):

1. Tabla `crop` truncada (no `crop_type` — los seeds de tipos deben mantenerse para FK).
2. Mocks de `CropRepository` y `I18nService` reseteados (`@BeforeEach`).
3. Para tests gRPC: stub regenerado, no compartido entre suites paralelas.
4. Para tests cross-service: WireMock reset, contadores limpios.

### 0.5 IDs de test y nomenclatura de archivos

| Sección | Prefijo | Archivo Java sugerido |
|---|---|---|
| 1 — POST | `CROP-1.NN` | `CropControllerCreateTest.java` |
| 2 — GET list | `CROP-2.NN` | `CropControllerListTest.java` |
| 3 — GET types | `CROP-3.NN` | `CropTypeControllerTest.java` |
| 4 — DELETE | `CROP-4.NN` | `CropControllerDeleteTest.java` |
| 5 — gRPC | `CROP-5.NN` | `CropGrpcServiceTest.java` |
| 6 — Repo | `CROP-6.NN` | `CropRepositoryIT.java` (Testcontainers) |
| 7 — Transversal | `CROP-7.NN` | `CropI18nTest.java`, `CropProblemDetailTest.java` |
| 8 — Seguridad | `CROP-8.NN` | `CropSecurityTest.java` |
| 9 — Filtro CUL-03 | `CROP-9.NN` | extiende `CropControllerListTest.java` |
| 10 — In-use | `CROP-10.NN` | `CropDeleteInUseTest.java` (🚧) |

> Cada `@DisplayName` debe contener el ID. Ejemplo: `@DisplayName("CROP-1.01: happy path mínimo")`.

---

## 1. Tests de `POST /crop` — alta de cultivo

> **Endpoint bajo prueba:** `POST /crop`. **Body:** `CropRequest{ name, description, crop_type_id }`. **Validación:** Bean Validation + check de existencia del tipo. **Estado HTTP de éxito:** 201 con body string i18n.

| Caso | ID | Request / Pre-condición | Resultado esperado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path mínimo | CROP-1.01 | `{name:"Trigo blando", description:"Trigo de invierno apto para harina", crop_type_id:1}` | 201; body `"Cultivo creado"`; fila persistida con UUID generado | WebMvc + JDBC | ❌ |
| Happy path con `Accept-Language: en` | CROP-1.02 | mismo body, header `Accept-Language: en` | 201; body `"Crop created"` | WebMvc | ❌ |
| `name` ausente | CROP-1.03 | body sin `name` | 400; `errors` contiene `"name: El nombre es requerido"` | WebMvc | ❌ |
| `name` vacío | CROP-1.04 | `"name":""` | 400; `errors` contiene `"name: El nombre es requerido"` (`@NotBlank`) | WebMvc | ❌ |
| `name` con solo espacios | CROP-1.05 | `"name":"   "` | 400; `@NotBlank` falla | WebMvc | ❌ |
| `name` < 3 chars | CROP-1.06 | `"name":"AB"` | 400; `errors` contiene `"name: El nombre debe tener entre 3 y 100 caracteres"` | WebMvc | ❌ |
| `name` > 100 chars | CROP-1.07 | `name` con 101 chars | 400; `errors` contiene `"name: El nombre debe tener entre 3 y 100 caracteres"` | WebMvc | ❌ |
| `name` exactamente 100 chars | CROP-1.08 | borde superior | 201 | WebMvc + JDBC | ❌ |
| `description` ausente | CROP-1.09 | sin `description` | 400; `errors` contiene `"description: La descripción es requerida"` | WebMvc | ❌ |
| `description` < 10 chars | CROP-1.10 | `"description":"corto"` | 400; `errors` contiene `"description: La descripción debe tener entre 10 y 500 caracteres"` | WebMvc | ❌ |
| `description` > 500 chars | CROP-1.11 | 501 chars | 400; mismo mensaje | WebMvc | ❌ |
| `description` exactamente 10 y 500 | CROP-1.12 | bordes | 201 ambos | WebMvc + JDBC | ❌ |
| `crop_type_id` ausente | CROP-1.13 | sin `crop_type_id` | 400; `errors` contiene `"crop_type_id: El tipo de cultivo es requerido"` | WebMvc | ❌ |
| `crop_type_id = 0` | CROP-1.14 | `"crop_type_id":0` | 400; `errors` contiene `"crop_type_id: El ID del tipo de cultivo debe ser positivo"` (`@Positive`) | WebMvc | ❌ |
| `crop_type_id` negativo | CROP-1.15 | `-1` | 400; mismo mensaje | WebMvc | ❌ |
| `crop_type_id` no numérico | CROP-1.16 | `"crop_type_id":"abc"` | 400 deserialización Jackson | WebMvc | ❌ |
| `crop_type_id` inexistente | CROP-1.17 | `999` | 400; `title:"No existe ese tipo de cultivo"`; `detail:"El tipo de cultivo no existe"` (clave `illegal.croptype.id`) | WebMvc + JDBC | ❌ |
| Body vacío | CROP-1.18 | `POST /crop` con `Content-Type: application/json` y body `{}` | 400; `errors` con tres entradas (los tres `@NotBlank/@NotNull`) | WebMvc | ❌ |
| Body no JSON | CROP-1.19 | `POST /crop` con `text/plain` | 415 Unsupported Media Type (Spring default) | WebMvc | ❌ |
| Sin Content-Type | CROP-1.20 | omitir el header | 415 | WebMvc | ❌ |
| Idempotencia: dos POST con mismo `name` | CROP-1.21 | dos requests idénticos | 201 + 201 (la BBDD permite duplicados — no hay UNIQUE en `name`); ambos persistidos con UUID distinto | JDBC | ❌ |
| Inserción concurrente | CROP-1.22 | 5 threads creando con mismo body | 5 filas insertadas, todas con UUID único | JDBC + concurrency | ❌ |
| Caracteres Unicode en `name` | CROP-1.23 | `"name":"Brócoli"` | 201; almacenado y devuelto correctamente | JDBC | ❌ |

### 1.A Asserts adicionales en happy path (CROP-1.01)

```java
// Tras 201, verificar la persistencia con un query directo
List<Map<String,Object>> rows = jdbcTemplate.queryForList(
    "SELECT id, name, description, crop_type_id FROM crop WHERE name = ?",
    "Trigo blando"
);
assertThat(rows).hasSize(1);
assertThat(rows.get(0).get("id")).isInstanceOf(UUID.class);
assertThat(rows.get(0).get("crop_type_id")).isEqualTo(1);
```

---

## 2. Tests de `GET /crop` — listado y proyección

> **Endpoint:** `GET /crop?fields=&crop_type_id=`. **Sin params** → todos los registros y todas las columnas. Validación de `fields` por whitelist; validación de `crop_type_id` por existencia.

| Caso | ID | Request / Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Lista vacía | CROP-2.01 | tabla `crop` vacía | 200; `[]` | WebMvc | ❌ |
| Lista con N elementos sin filtros | CROP-2.02 | seed 3 cultivos | 200; array de 3 objetos con `id, name, description, crop_type_id` | JDBC + WebMvc | ❌ |
| `fields=id` | CROP-2.03 | seed 3 | 200; cada objeto solo con `id` | WebMvc | ❌ |
| `fields=id,name` | CROP-2.04 | seed 3 | 200; cada objeto con `id` y `name` | WebMvc | ❌ |
| `fields` con espacios | CROP-2.05 | `?fields=id, name , description` | 200; trim aplicado, sin error | WebMvc | ❌ |
| `fields` en mayúsculas | CROP-2.06 | `?fields=ID,NAME` | 200; lowercase aplicado | WebMvc | ❌ |
| `fields` con duplicados | CROP-2.07 | `?fields=id,id,name` | 200; sin error (Set deduplica) | WebMvc | ❌ |
| `fields` vacío | CROP-2.08 | `?fields=` | 200; equivale a `*` | WebMvc | ❌ |
| `fields` con campo no permitido | CROP-2.09 | `?fields=secret` | 400; `title:"Campos invalido"`; `detail` lista campos permitidos | WebMvc | ❌ |
| `fields` mezcla válidos+inválidos | CROP-2.10 | `?fields=id,secret` | 400; sin tocar BBDD | WebMvc | ❌ |
| `fields` con SQL injection | CROP-2.11 | `?fields=id;DROP TABLE crop;--` | 400; **NO** ejecuta SQL | WebMvc + JDBC | ❌ |
| `crop_type_id` válido y existente | CROP-2.12 | seed con 2 cultivos `crop_type_id=1` y 1 `crop_type_id=2`; `?crop_type_id=1` | 200; array de 2 elementos | JDBC | ❌ |
| `crop_type_id` válido y sin matches | CROP-2.13 | seed sin cultivos de tipo 4; `?crop_type_id=4` | 200; `[]` | JDBC | ❌ |
| `crop_type_id` inexistente | CROP-2.14 | `?crop_type_id=999` | 400; `title:"No existe ese tipo de cultivo"`; **no** lanza query a `crop` | WebMvc | ✅ (parcial — cubre status 400 vía service test) |
| `crop_type_id` 0 | CROP-2.15 | `?crop_type_id=0` | 400; tipo no existe (no hay seed con id 0) | WebMvc | ❌ |
| `crop_type_id` negativo | CROP-2.16 | `?crop_type_id=-1` | 400 | WebMvc | ❌ |
| `crop_type_id` no numérico | CROP-2.17 | `?crop_type_id=abc` | 400 binding | WebMvc | ✅ |
| `fields + crop_type_id` combinados | CROP-2.18 | `?fields=id,name&crop_type_id=1` | 200; array con solo `id` y `name` filtrado | WebMvc + JDBC | ✅ (cubre binding; falta integración) |
| Encoding UTF-8 en respuesta | CROP-2.19 | seed con `name="Brócoli"` | 200; chars UTF-8 correctos en JSON | WebMvc | ❌ |
| Cache headers | CROP-2.20 | request normal | 200 sin cabeceras `Cache-Control` privadas (catálogo es global y cachable) | WebMvc | 🟡 (decisión pendiente) |

---

## 3. Tests de `GET /crop/type` — catálogo de tipos

> **Endpoint:** `GET /crop/type`. **Sin parámetros.** Devuelve todos los tipos `crop_type` ordenados por `id`.

| Caso | ID | Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Devuelve los 5 seeds | CROP-3.01 | BBDD recién migrada | 200; array de 5 con names `CEREAL, FRUIT, VEGETABLE, TUBER, LEGUME` | JDBC + WebMvc | ❌ |
| Orden estable | CROP-3.02 | mismo seed | el orden por `id` ascendente debe ser determinista en cada llamada | WebMvc | ❌ |
| Inserto un tipo nuevo | CROP-3.03 | `INSERT INTO crop_type(name) VALUES ('OILSEED')` | 200; array de 6 incluyendo el nuevo | JDBC | ❌ |
| Tabla vacía (caso patológico) | CROP-3.04 | `TRUNCATE crop_type CASCADE` | 200; `[]` (no error) | JDBC | ❌ |
| `id` y `name` correctamente serializados | CROP-3.05 | seed | objeto JSON con dos campos exactos | WebMvc | ❌ |

---

## 4. Tests de `DELETE /crop/{id}` — borrado seguro

> **Endpoint:** `DELETE /crop/{id}`. **Estado actual** (antes de §10): borra físicamente sin protección. **Estado objetivo** (tras implementar §10): rechaza si el cultivo está en uso por al menos una `season`.
>
> Esta sección cubre el comportamiento **mínimo correcto del endpoint en sí**. Los tests de la protección "in-use" están en §10.

| Caso | ID | Request / Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path | CROP-4.01 | crop existente sin seasons | 204; fila eliminada de BBDD | WebMvc + JDBC | ❌ |
| `id` mal formado (no UUID) | CROP-4.02 | `DELETE /crop/abc` | 400 binding | WebMvc | ❌ |
| `id` UUID válido pero inexistente | CROP-4.03 | UUID aleatorio | 400; `title:"No existe ese tipo de cultivo"` (mensaje incorrecto pero es lo que devuelve hoy — ver deuda) | WebMvc | ❌ |
| Doble DELETE | CROP-4.04 | DELETE OK + DELETE con mismo id | 204 + 400 (ya no existe) | WebMvc + JDBC | ❌ |
| DELETE concurrente | CROP-4.05 | dos threads DELETE el mismo id | uno gana con 204, el otro 400 | JDBC | ❌ |
| 204 con body (deuda 7 del guide) | CROP-4.06 | request normal | hoy devuelve body de mensaje; clientes deben ignorarlo | WebMvc | ❌ |

> **Importante (estado objetivo):** una vez implementada la protección §10, el caso CROP-4.01 cambia: requiere "crop sin seasons asociadas". Si tiene seasons → debe ir al §10.

### 4.A Refactor pendiente del título del error 400

Hoy `IllegalArgumentException("crop not found")` cae en el handler genérico que pone `title:"No existe ese tipo de cultivo"`. **Corregir** el handler para diferenciar:

- `crop_type_id` desconocido → `title:"Crop type not found"`.
- `id` de crop a borrar inexistente → `title:"Crop not found"`.

Esto exige excepciones custom: `CropTypeNotFoundException` y `CropNotFoundException`. Añadir tests CROP-4.03b y CROP-1.17b cuando esto se implemente.

---

## 5. Tests de gRPC `CheckCropExists`

> **RPC:** `CheckCropExists(CropIdRequest{crop_id}) → CropExistsResponse{exists}`. Servidor en `:9094`. Plaintext.

| Caso | ID | Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Crop existe | CROP-5.01 | seed con UUID `X` | `CheckCropExists("X")` → `exists=true` | gRPC + JDBC | ❌ |
| Crop no existe | CROP-5.02 | UUID aleatorio | `exists=false` | gRPC | ❌ |
| `crop_id` no es UUID | CROP-5.03 | `"abc"` | `exists=false` (sin error) | gRPC | ❌ |
| `crop_id` vacío | CROP-5.04 | `""` | `exists=false` | gRPC | ❌ |
| `crop_id` con UUID malformado parcial | CROP-5.05 | `"12345"` | `exists=false` | gRPC | ❌ |
| BBDD caída | CROP-5.06 | parar el contenedor PG durante el test | RPC propaga error gRPC `UNAVAILABLE` o `INTERNAL`; el cliente debe poder retry | gRPC + Testcontainers | ❌ |
| Latencia alta | CROP-5.07 | añadir delay artificial | el cliente debe respetar timeout (configurable) | gRPC | 🟡 |
| Múltiples llamadas concurrentes | CROP-5.08 | 100 calls paralelas | todas responden correctamente sin interferencia | gRPC | ❌ |

---

## 6. Tests del repositorio (BBDD real con Testcontainers)

> **Capa:** `@JdbcTest + @Import(CropRepository.class) + @Testcontainers`. Verifica SQL puro, mapeos y constraints.

| Caso | ID | Operación | Resultado | Estado |
|---|---|---|---|---|
| `insertCrop` con `crop_type_id` válido | CROP-6.01 | `insertCrop("trigo","desc",1)` | devuelve 1; fila insertada con UUID generado | ❌ |
| `insertCrop` con `crop_type_id` FK violation | CROP-6.02 | `insertCrop("x","y",999)` | lanza `DataIntegrityViolationException` (FK) — el service debe atraparlo antes con `cropTypeExists` | ❌ |
| `cropTypeExists(1)` true | CROP-6.03 | seed `CEREAL` | `true` | ❌ |
| `cropTypeExists(999)` false | CROP-6.04 | id ausente | `false` | ❌ |
| `cropExists(uuid)` true / false | CROP-6.05 | mismo patrón | `true`/`false` según seed | ❌ |
| `findAllCrops("*", null)` | CROP-6.06 | seed 3 | 3 maps con todas las columnas | ❌ |
| `findAllCrops("id, name", null)` | CROP-6.07 | seed 3 | 3 maps con solo `id` y `name` | ❌ |
| `findAllCrops("*", 1)` | CROP-6.08 | seed mixto | filas filtradas por `crop_type_id` | ❌ |
| `findAllCrops("*", 999)` | CROP-6.09 | seed mixto | `[]` (filtro válido a nivel SQL aunque el tipo no exista — el service debe haber filtrado antes) | ❌ |
| `findAllCropTypes` orden | CROP-6.10 | tras V1 | lista ordenada por `id` con los 5 seeds | ❌ |
| `deleteCrop(uuid)` exitoso | CROP-6.11 | seed 1 | devuelve 1; fila eliminada | ❌ |
| `deleteCrop(uuid)` inexistente | CROP-6.12 | UUID aleatorio | lanza `IllegalArgumentException` (lo lanza el repo en `cropExists` previo) | ❌ |
| Caracteres especiales en `name` | CROP-6.13 | `"O'Higgins'", "naïve"` | inserción y lectura sin escapes manuales | ❌ |
| Default UUID generado por Postgres | CROP-6.14 | `INSERT` sin id | la fila trae un UUID v4 aleatorio (no zero) | ❌ |
| Índice `idx_crop_name` usado | CROP-6.15 | `EXPLAIN SELECT * FROM crop WHERE name='X'` | plan de ejecución usa el índice (smoke) | ❌ |

### 6.A Configuración Testcontainers

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

Flyway debe aplicar `V1` automáticamente. Validar: `SELECT COUNT(*) FROM flyway_schema_history` ≥ 1.

---

## 7. Tests transversales (i18n, ProblemDetail, headers, locales)

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| Locale español default | CROP-7.01 | sin `Accept-Language` | mensajes en `es` | ❌ |
| Locale inglés | CROP-7.02 | `Accept-Language: en` | mensajes en `en` (cuando la clave esté traducida) | ❌ |
| Locale desconocido | CROP-7.03 | `Accept-Language: zh` | fallback a `en` (que a su vez en algunas claves está vacío) | ❌ |
| Multi-locale fallback | CROP-7.04 | `Accept-Language: zh, es;q=0.5` | usa `es` | ❌ |
| `ProblemDetail` content-type | CROP-7.05 | cualquier 4xx | `Content-Type: application/problem+json` | ❌ |
| `ProblemDetail` campos mínimos | CROP-7.06 | cualquier 4xx | contiene `type`, `title`, `status`, `detail` | ❌ |
| `errors[]` en validation 400 | CROP-7.07 | request inválido | `errors` array con `<campo>: <mensaje>` | ❌ |
| Content-Type request `application/problem+json` | CROP-7.08 | POST con ese content-type | rechaza (415) — solo se acepta `application/json` para body | ❌ |
| Charset UTF-8 explícito | CROP-7.09 | response | `Content-Type: application/json;charset=UTF-8` | ❌ |
| CORS preflight | CROP-7.10 | OPTIONS desde origin | depende de configuración del gateway, no del servicio | 🟡 (out of scope local) |

---

## 8. Tests de seguridad (defensivos)

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| SQL injection vía `fields` | CROP-8.01 | `?fields=id;DROP TABLE crop;--` | 400; tabla intacta | ❌ |
| SQL injection vía `crop_type_id` | CROP-8.02 | `?crop_type_id=1 OR 1=1` | 400 binding (no es Integer) | ❌ |
| Body con propiedades extra (Jackson) | CROP-8.03 | añade `"isAdmin":true` al `CropRequest` | 200/201 (Jackson ignora por default); no se persiste nada raro | ❌ |
| Body con tipos inesperados | CROP-8.04 | `"name":123` | 400 deserialización | ❌ |
| Payload gigante | CROP-8.05 | body con 50 MB | rechazado por límite de tamaño de Spring (default 1 MB) | ❌ |
| Path traversal en `id` | CROP-8.06 | `DELETE /crop/../../etc/passwd` | 400 binding (no UUID) | ❌ |
| Header injection | CROP-8.07 | `Accept-Language: es\r\nX-Injected: 1` | rechazado por servlet container | ❌ |
| Auth bypass | CROP-8.08 | sin Authorization header (hoy es público) | 200/201 según endpoint — documentado como deuda nº 5 | ❌ |
| Rate-limit | CROP-8.09 | 1000 POST /crop en 1 s | sin rate-limit hoy; documentado como mejora futura | 🟡 |

---

## 9. Tests del filtro `crop_type_id` (Paquete 03)

> Estos tests **ya tienen un mínimo cubierto** tras el commit `7840e43` (CropServiceTest, CropControllerTest). Esta sección los **completa** con casos de integración + JDBC.

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| Filtro válido devuelve subconjunto | CROP-9.01 | seed: 2 cultivos tipo 1, 1 tipo 2; `?crop_type_id=1` | 200; array de 2 | ✅ (unit) — falta JDBC |
| Filtro válido sin matches | CROP-9.02 | sin cultivos tipo 5; `?crop_type_id=5` | 200; `[]` | ❌ |
| Filtro con `fields` combinado | CROP-9.03 | `?fields=id&crop_type_id=1` | 200; objetos solo con `id` | ✅ (unit) — falta JDBC |
| Filtro con `crop_type_id` desconocido | CROP-9.04 | `?crop_type_id=999` | 400; `cropTypeExists` debe ejecutarse antes de la query principal | ✅ (unit) |
| Filtro con `crop_type_id` no entero | CROP-9.05 | `?crop_type_id=abc` | 400 binding | ✅ (MockMvc) |
| Filtro `crop_type_id=0` | CROP-9.06 | id imposible | 400 (no existe en `crop_type`) | ❌ |
| Filtro y `fields` con campo no válido | CROP-9.07 | `?fields=secret&crop_type_id=1` | 400; orden importa: el `FieldsValidator` debe ejecutarse antes de `cropTypeExists` para no malgastar query | ❌ |
| Repositorio: SQL parametrizado | CROP-9.08 | inyección de prepared statement | el query log debe mostrar `WHERE crop_type_id = ?` no interpolado | ❌ |
| Performance: filtro vs full scan | CROP-9.09 | 10 000 filas, comparar tiempo con/sin filtro | el filtro debe ser ≥ 5× más rápido (índice por crop_type_id pendiente) | 🚧 (requiere índice nuevo) |

### 9.A Mejora propuesta a la migración (no obligatoria)

```sql
-- Aplicar como V2__index_crop_type_id.sql cuando el filtro vea volumen real
CREATE INDEX idx_crop_crop_type_id ON crop(crop_type_id);
```

Test asociado: `CROP-9.10` — verificar `EXPLAIN ANALYZE` usa el índice.

---

## 10. Tests de la protección "cultivo en uso" (NUEVO — bloque 🚧)

> **Toda esta sección depende de implementar primero la protección.** El objetivo: **un cultivo referenciado por al menos una `season` no puede borrarse**, y al borrarlo (cuando NO está en uso) los servicios consumidores deben enterarse para no quedar inconsistentes.

### 10.1 Decisión arquitectónica

Existen tres caminos para implementar la protección. El test plan prevé el **Camino A** como recomendado y deja constancia de los otros.

**Camino A — Verificación síncrona vía gRPC inverso (recomendado):**

- `season-service` expone un nuevo RPC `CheckCropInUse(crop_id) → InUseResponse{ in_use, season_count }`.
- `crop-service` añade un cliente gRPC hacia `season-service` y llama antes del DELETE.
- Si `in_use=true` → 409 `crop.in.use` con detalle `{ "season_count": N }`.

> Esto invierte parcialmente la dependencia (`crop-service` pasa a depender de `season-service`), lo cual el §8.4 del guide marca como antipatrón. **Aceptable** porque la dependencia es estrictamente para integridad referencial, no para lógica de dominio. Documentar la decisión en el ADR.

**Camino B — Verificación asíncrona vía Kafka (más desacoplado, eventualmente consistente):**

- `crop-service` publica `crop-deleted` al borrar.
- `season-service` consume y marca/borra/avisa.
- **Limitación:** no protege contra orfandad, solo limpia post-mortem. Requiere combinarlo con A para una protección estricta.

**Camino C — Mover la responsabilidad al gateway (rechazado):**

- `api-gateway` orquesta la doble llamada antes de propagar el DELETE.
- Mete lógica de dominio en infraestructura → antipatrón.

### 10.2 Cambios mínimos en el código (Camino A)

Archivos a tocar:

```
season-service/src/main/proto/season.proto                        (new RPC + messages)
season-service/src/main/java/.../grpc/SeasonGrpcService.java      (server)
season-service/src/main/java/.../repository/SeasonRepository.java  (countByCropId)

crop-service/src/main/proto/season.proto                          (copy proto)
crop-service/src/main/java/.../grpc/SeasonGrpcClient.java         (new client)
crop-service/src/main/java/.../service/CropService.java           (deleteCrop usa client)
crop-service/src/main/java/.../exception/CropInUseException.java  (new)
crop-service/src/main/java/.../exception/GlobalExceptionHandler.java  (handler 409)
crop-service/src/main/resources/i18n/messages_es.properties        (crop.in.use)
crop-service/src/main/resources/i18n/messages.properties           (crop.in.use)
crop-service/pom.xml                                                (grpc-client-spring-boot-starter — ya está)
crop-service/src/main/resources/application-dev.properties        (grpc.client.season-service.address)
crop-service/src/main/resources/application-prod.properties       (...)
```

### 10.3 Casos de test del bloque 🚧

| Caso | ID | Pre / Request | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| DELETE con 0 seasons → 204 | CROP-10.01 | crop sin uso; `season-service.CheckCropInUse → in_use=false` | 204; fila eliminada | WebMvc + integración | 🚧 |
| DELETE con 1 season → 409 | CROP-10.02 | `CheckCropInUse → in_use=true, season_count=1` | 409; `title:"Crop in use"`; `detail:"El cultivo está en uso por 1 temporada"`; **fila NO eliminada** | WebMvc + integración | 🚧 |
| DELETE con N seasons → 409 con conteo | CROP-10.03 | `season_count=5` | 409; mensaje plural; conteo en el `ProblemDetail` como propiedad `seasonCount` | WebMvc | 🚧 |
| DELETE con `season-service` caído → 503 | CROP-10.04 | gRPC `UNAVAILABLE` | 503; **fila NO eliminada** (fail-closed); `title:"Service unavailable"`; `detail:"No se puede verificar el uso del cultivo"` | WebMvc + integración | 🚧 |
| DELETE con `season-service` lento (timeout) | CROP-10.05 | RPC excede `deadline=2s` | 503; mismo comportamiento que 10.04 | WebMvc + integración | 🚧 |
| Doble DELETE consecutivo, sin uso → 204 + 400 | CROP-10.06 | secuencia DELETE OK + DELETE | 204 + 400 (segunda vez no existe) | integración | 🚧 |
| Race condition: insert season durante DELETE | CROP-10.07 | thread A: DELETE crop; thread B: insert season con ese `crop_id` justo entre check y delete | la implementación debe usar transacción + lock optimista (RU) o consultar y borrar en mismo statement con guarda. **Documentar como TOCTOU conocido**: si pasa, gana el último; el `season-service` recibe un `crop-deleted` y compensa | integración + concurrency | 🚧 |
| RPC contract: `CheckCropInUse` schema | CROP-10.08 | snapshot del `.proto` y de los stubs | el contract test debe fallar si alguien rompe la firma | contract test | 🚧 |
| i18n del 409 ES y EN | CROP-10.09 | request con `Accept-Language: es` y `en` | mensajes correctos en ambos idiomas | WebMvc | 🚧 |
| Auditoría: DELETE rechazado se loggea | CROP-10.10 | DELETE → 409 | log a nivel `WARN` con `crop_id`, `season_count`, `requester_ip` | unit + log capture | 🚧 |
| Métrica: counter `crop.delete.rejected` | CROP-10.11 | DELETE → 409 | si se añade `micrometer`, el counter incrementa | unit | 🚧 |

### 10.4 Casos de test del flujo Kafka complementario (Camino B opcional)

| Caso | ID | Escenario | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| `crop-deleted` se publica tras 204 | CROP-10.20 | DELETE OK | mensaje en topic `crop-deleted` con payload `{ "cropId": UUID, "deletedAt": Instant }` | integración + Testcontainers Kafka | 🚧 |
| `crop-deleted` NO se publica si 409/503 | CROP-10.21 | DELETE rechazado | topic vacío (verificar con consumer test) | integración | 🚧 |
| `crop-deleted` payload conforme con consumidor | CROP-10.22 | golden file del JSON | `season-service.CropDeletedListener` debe consumir sin errores | contract | 🚧 |

### 10.5 Casos de test del flujo end-to-end

| Caso | ID | Escenario completo | Resultado |
|---|---|---|---|
| Flujo "intento borrar con uso, cierro temporada, vuelvo a borrar" | CROP-10.30 | (1) POST crop → 201; (2) POST season que usa ese crop → 201; (3) DELETE crop → 409; (4) PATCH season para cerrarla — _esto NO libera_ ; (5) DELETE season → 204; (6) DELETE crop → 204 | flujo completo green |
| Flujo "borrado idempotente desde frontend" | CROP-10.31 | el frontend reintenta DELETE tras 409 (ej. tras cerrar manualmente la season que lo usaba) | el segundo DELETE debe ir 204 |

---

## Apéndice A — Matriz de cobertura por capa

| Capa | Casos previstos | Casos hoy verdes | Bloque que los implementa |
|---|---|---|---|
| Unit (Mockito) | ~25 | 4 | §1 (CROP-1.NN), §2.14, §2.17, §2.18, §9.04 |
| WebMvc slice | ~50 | 4 | §1, §2, §3, §4, §7, §9 |
| JDBC (Testcontainers) | ~25 | 0 | §6 entero, §1.A, §2.18 |
| gRPC | ~8 | 0 | §5 entero |
| Integración full | ~12 | 1 (contextLoads) | §10, §6.15 |
| Contract / WireMock | ~5 | 0 | §10.22 |
| **Total** | **~125** | **9** | |

> Objetivo de cobertura JaCoCo tras implementar todo: ≥ 70 % en `crop-service` (criterio del paquete 03 §6.2).

---

## Apéndice B — Fixtures listas para copiar

### B.1 `CropRequest` válido (mínimo)

```json
{
  "name": "Trigo blando",
  "description": "Trigo de invierno apto para harina panificable",
  "crop_type_id": 1
}
```

### B.2 `CropRequest` borde inferior

```json
{
  "name": "ABC",
  "description": "abcdefghij",
  "crop_type_id": 1
}
```

### B.3 `CropRequest` borde superior

```json
{
  "name": "<100 chars exactos>",
  "description": "<500 chars exactos>",
  "crop_type_id": 5
}
```

### B.4 `CropRequest` inválido (todos los campos a la vez)

```json
{
  "name": "",
  "description": "",
  "crop_type_id": -1
}
```

### B.5 SQL seeds para tests JDBC

```sql
-- Limpiar (no truncar crop_type — los tipos son seeds del V1)
TRUNCATE TABLE crop;

INSERT INTO crop (id, name, description, crop_type_id) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Trigo blando', 'Trigo de invierno', 1),
  ('22222222-2222-2222-2222-222222222222', 'Manzana Reineta', 'Frutal de pepita', 2),
  ('33333333-3333-3333-3333-333333333333', 'Tomate raf', 'Hortícola de invernadero', 3);
```

### B.6 gRPC request example

```json
{ "crop_id": "11111111-1111-1111-1111-111111111111" }
```

### B.7 Mock de `season-service.CheckCropInUse` con WireMock

```java
stubFor(post(urlEqualTo("/com.agro.season.grpc.SeasonService/CheckCropInUse"))
    .willReturn(aResponse()
        .withHeader("Content-Type", "application/grpc")
        .withBody(/* protobuf bytes con in_use=false, season_count=0 */)));
```

> Para gRPC con WireMock se necesita la extensión `wiremock-grpc-extension` (o usar `InProcessServer` directamente, más simple).

---

## Apéndice C — Orden de implementación sugerido

> Recomendación para que el desarrollador (humano o agente) avance sin bloqueos.

### Fase 1 — completar coverage del estado actual (sin nueva funcionalidad)

1. Implementar §1 entero (`CropControllerCreateTest` + `CropServiceTest` extendidos). Un commit.
2. Implementar §2 entero. Un commit.
3. Implementar §3 entero. Un commit.
4. Implementar §4 sin protección (estado actual). Un commit. **Documentar** que CROP-4.03 hoy devuelve título incorrecto.
5. Implementar §5 entero (`CropGrpcServiceTest`). Un commit.
6. Implementar §6 entero (Testcontainers). Un commit.
7. Implementar §7 transversal. Un commit.
8. Implementar §8 defensivo. Un commit.
9. Implementar §9 (completa los huecos JDBC). Un commit.

**Hito:** todos los tests verdes; cobertura JaCoCo ≥ 60 %; refactor del handler 4.A para diferenciar `CropTypeNotFoundException` y `CropNotFoundException`.

### Fase 2 — protección "in-use" (bloque 🚧)

1. **`season-service`:** añadir `CheckCropInUse` al `.proto`, server impl, repo `countByCropId`. Tests del lado del server.
2. **`crop-service`:** copiar `.proto`, crear `SeasonGrpcClient`, integrarlo en `CropService.deleteCrop`. Crear `CropInUseException` + handler 409.
3. Implementar §10.01..10.11 en `crop-service`.
4. (Opcional) Implementar §10.20..10.22 si se decide complementar con Kafka.
5. Implementar §10.30..10.31 (end-to-end) en una suite de integración multi-servicio (`@SpringBootTest` arrancando ambos servicios o WireMock).

**Hito:** todos los tests 🚧 verdes; el endpoint DELETE pasa a estar funcionalmente seguro; cobertura ≥ 70 %.

### Fase 3 — auth + observabilidad (deuda restante de §10 del guide)

1. Activar `JwtValidation` en `/api/crop/**` del gateway.
2. Aplicar autorización admin para POST/DELETE; lectura pública para GET.
3. Añadir actuator `/actuator/health`.
4. Añadir métricas micrometer (CROP-10.11).

> Estas mejoras quedan fuera del paquete 03 estricto pero el plan de tests las menciona porque son la condición necesaria para que el servicio pase a producción.

---

## Notas finales

1. **Cualquier cambio en este plan debe ir acompañado de un commit que actualice el `.md` y los tests asociados** — ambos archivos van juntos.
2. **No mezclar tests de fases distintas en el mismo PR**: los 🚧 deben ir solos para que el revisor pueda razonar sobre el contrato cross-service sin distraerse con el resto.
3. **Si un caso del plan es difícil de cubrir** (concurrencia, tiempos, fallo de infra) → marcarlo como `@Tag("flaky")` o `@Disabled` con justificación, **nunca** silenciar la causa raíz.
4. **La fuente de verdad del comportamiento es el código + este plan + `CROP_SERVICE_GUIDE.md`**. Si los tres divergen, abrir issue.
