# `task-service` — Plan de tests exhaustivo

> Documento de referencia para que un desarrollador (humano o agente IA) implemente y verifique **todos** los casos de comportamiento del microservicio `task-service`. Equivalente al `season-service/test-suite-plan-season-service.md`, adaptado al dominio de tareas, evidencias, transiciones, notificaciones y hub Kafka (D5).
>
> **Audiencia:** desarrollador que va a aterrizar la suite completa de tests.
>
> **Fuentes de verdad consultadas para construir este plan:**
> - `task-service/TASK-SERVICE-DOCUMENTATION.md` (contratos REST, Kafka, esquema BBDD).
> - `LLM-WORK/04-task-service-from-main.md` (HU-TAR-01..04 + decisiones D1, D2, D5).
> - `IOT-TASK-INPUT-DESIGN.md` §11 (decisiones bloqueadas).
> - `MICROSERVICES-RELATIONSHIPS.md` (relaciones cross-servicio).
> - Código actual en `feat-task-service` (62 tests existentes a fecha 2026-05-11).
>
> **Identificadores estables:** cada caso lleva un ID `TASK-XX.YY`. Úsalo en `@DisplayName` y en commits/PRs para trazar 1:1 plan ↔ JUnit ↔ script bash.
>
> **Estado de cada caso:**
> - ✅ ya cubierto en `feat-task-service`.
> - 🟡 parcialmente cubierto (falta capa o caso límite).
> - ❌ no cubierto.
> - 🚧 requiere implementar funcionalidad previa (ownership cross-service, MailService SMTP real, etc.).

---

## Índice

- [0. Convenciones y plantilla común](#0-convenciones-y-plantilla-común)
- [1. `POST /task` — creación de tarea (HU-TAR-01)](#1-post-task--creación-de-tarea-hu-tar-01)
- [2. `GET /task/{id}` — detalle con proyección](#2-get-taskid--detalle-con-proyección)
- [3. `GET /task` — listado con filtros y paginación](#3-get-task--listado-con-filtros-y-paginación)
- [4. `GET /task/calendar` — vista calendario](#4-get-taskcalendar--vista-calendario)
- [5. `PATCH /task/{id}` — actualización](#5-patch-taskid--actualización)
- [6. `DELETE /task/{id}` — política de borrado](#6-delete-taskid--política-de-borrado)
- [7. `POST /task/{id}/transition` — máquina de estados (HU-TAR-02)](#7-post-taskidtransition--máquina-de-estados-hu-tar-02)
- [8. Endpoints de evidencias](#8-endpoints-de-evidencias)
- [9. `GET /task/dashboard` (HU-TAR-03)](#9-get-taskdashboard-hu-tar-03)
- [10. `GET /task/export.csv` — exportación](#10-get-taskexportcsv--exportación)
- [11. Role scoping (admin / agricultor / técnico)](#11-role-scoping-admin--agricultor--técnico)
- [12. Endpoints de notificaciones (HU-TAR-04)](#12-endpoints-de-notificaciones-hu-tar-04)
- [13. `NotificationSchedulerService` — UPCOMING / OVERDUE / DIGEST](#13-notificationschedulerservice--upcoming--overdue--digest)
- [14. gRPC clients (UserGrpcClient, TerrainGrpcClient)](#14-grpc-clients-usergrpcclient-terraingrpcclient)
- [15. Listener `user-deleted` — política D2](#15-listener-user-deleted--política-d2)
- [16. Listener `terrain-deleted` — cascada](#16-listener-terrain-deleted--cascada)
- [17. Listener `stock-low` — hub D5](#17-listener-stock-low--hub-d5)
- [18. Listener `sensor-alert` — hub D5](#18-listener-sensor-alert--hub-d5)
- [19. Producer Kafka `task-completed`](#19-producer-kafka-task-completed)
- [20. Repositorio (BBDD real con Testcontainers)](#20-repositorio-bbdd-real-con-testcontainers)
- [21. `RecurrenceExpander`](#21-recurrenceexpander)
- [22. `FieldsValidator`](#22-fieldsvalidator)
- [23. `FileStorageService`](#23-filestorageservice)
- [24. Integración cross-service](#24-integración-cross-service)
- [25. Transversales (i18n, ProblemDetail, locales, headers)](#25-transversales-i18n-problemdetail-locales-headers)
- [26. Seguridad defensiva](#26-seguridad-defensiva)
- [27. Bloque ownership 🚧 (futuro)](#27-bloque-ownership--futuro)
- [Apéndice A — Matriz de cobertura por capa](#apéndice-a--matriz-de-cobertura-por-capa)
- [Apéndice B — Fixtures listas para copiar](#apéndice-b--fixtures-listas-para-copiar)
- [Apéndice C — Orden de implementación sugerido](#apéndice-c--orden-de-implementación-sugerido)

---

## 0. Convenciones y plantilla común

### 0.1 Capas de test

| Capa | Tecnología | Cuándo usarla |
|---|---|---|
| **Unit** | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Lógica del service y excepciones. Todos los colaboradores mockeados. |
| **WebMvc slice** | `MockMvcBuilders.standaloneSetup(controller)` + Mockito | Status HTTP, JSON mapping, headers, validación de request. El service va mockeado. |
| **JDBC slice** | `@JdbcTest + @Import(<Repo>.class)` con Testcontainers Postgres 14 | SQL real, mapeos, constraints, JSONB serialization. Nada de servlets. |
| **gRPC unit** | Mock del stub blocking + `@InjectMocks` | Verifica que el cliente envuelve `StatusRuntimeException` en `RuntimeException`. |
| **Kafka listener unit** | Mock del service + invocación directa del listener | Smoke; el listener delega correctamente. |
| **Kafka producer unit** | `@MockBean KafkaTemplate` + spy sobre `EventPublisher` | Verifica que se llama `send(topic, key, value)` con el payload correcto. |
| **Kafka integración** | `@SpringBootTest + @EmbeddedKafka` o broker Testcontainers | Producer/consumer end-to-end con `__TypeId__` headers. |
| **Integración full** | `@SpringBootTest(webEnvironment=RANDOM_PORT) + @Testcontainers` | Flyway + JdbcTemplate + REST real + listener Kafka real. |
| **Cross-service** | `@SpringBootTest` + WireMock para gRPC + Testcontainers Kafka | Flujo end-to-end POST /task → CheckTerrainExists/ValidateUser → INSERT → publish. |
| **Contract test** | snapshot de `.proto` y de los stubs | Verifica que el cliente sigue compatible si el servidor remoto cambia. |
| **Concurrency / load** | JUnit + `ExecutorService` o JMH | Casos TOCTOU, anti-spam bajo carga, scheduler en paralelo. |

### 0.2 Plantilla de aserciones para errores

Toda respuesta de error usa `ProblemDetail` (RFC 7807):

```java
mockMvc.perform(...)
    .andExpect(status().is(<status>))
    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
    .andExpect(jsonPath("$.status").value(<status>))
    .andExpect(jsonPath("$.title").value("<Title>"))
    .andExpect(jsonPath("$.detail").value(containsString("<i18n key o trozo>")));
```

### 0.3 Mensajes i18n

- En **unit tests**, mockear `I18nService` para que devuelva la propia clave: `when(i18n.getMessage(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));`. Asserts contra claves (`task.transition.invalid`), no contra texto traducido.
- En **WebMvc/integración**, alternar `Accept-Language: es` y `Accept-Language: en` y verificar que el `AcceptHeaderLocaleResolver` resuelve correctamente.

### 0.4 Estado inicial supuesto

Antes de cada test (donde aplique):

1. Tablas `task`, `task_evidence`, `task_state_history`, `notification`, `notification_preference`, `notification_emission_log` truncadas. **No** truncar `task_type` — los seeds del V1 (SOWING/IRRIGATION/FERTILIZATION/TREATMENT/HARVEST/OTHER) deben mantenerse para FK.
2. Mocks reseteados (`@BeforeEach`).
3. Tests de listener Kafka: stub regenerado, no compartido entre suites paralelas.
4. Tests cross-service: WireMock reset, contadores limpios.
5. `FileStorageService`: directorio temporal por test (`@TempDir`), no compartir entre runs.

### 0.5 IDs de test y nomenclatura de archivos

| Sección | Prefijo | Archivo Java sugerido |
|---|---|---|
| 1 — POST | `TASK-1.NN` | `controller/TaskControllerCreateTest.java` |
| 2 — GET detail | `TASK-2.NN` | `controller/TaskControllerDetailTest.java` |
| 3 — GET list | `TASK-3.NN` | `controller/TaskControllerListTest.java` |
| 4 — Calendar | `TASK-4.NN` | `controller/TaskControllerCalendarTest.java` |
| 5 — PATCH | `TASK-5.NN` | `controller/TaskControllerUpdateTest.java` |
| 6 — DELETE | `TASK-6.NN` | `controller/TaskControllerDeleteTest.java` |
| 7 — Transition | `TASK-7.NN` | `service/TaskTransitionServiceTest.java` + `controller/TaskControllerTransitionTest.java` |
| 8 — Evidence | `TASK-8.NN` | `controller/TaskEvidenceControllerTest.java` + `service/TaskEvidenceServiceTest.java` |
| 9 — Dashboard | `TASK-9.NN` | `controller/TaskDashboardControllerTest.java` |
| 10 — Export | `TASK-10.NN` | `service/TaskExportServiceTest.java` |
| 11 — Role scoping | `TASK-11.NN` | `service/RoleScopingServiceTest.java` |
| 12 — Notif endpoints | `TASK-12.NN` | `controller/NotificationControllerTest.java` |
| 13 — Scheduler | `TASK-13.NN` | `service/NotificationSchedulerServiceTest.java` |
| 14 — gRPC | `TASK-14.NN` | `client/UserGrpcClientTest.java`, `client/TerrainGrpcClientTest.java` |
| 15 — user-deleted | `TASK-15.NN` | `listener/UserDeletedListenerTest.java` + `service/TaskServiceUserDeletedTest.java` |
| 16 — terrain-deleted | `TASK-16.NN` | `listener/TerrainDeletedListenerTest.java` |
| 17 — stock-low | `TASK-17.NN` | `listener/StockLowListenerTest.java` |
| 18 — sensor-alert | `TASK-18.NN` | `listener/SensorAlertListenerTest.java` |
| 19 — Producer | `TASK-19.NN` | `kafka/EventPublisherTest.java` + `kafka/TaskCompletedKafkaIT.java` |
| 20 — Repository (Testcontainers) | `TASK-20.NN` | `repository/TaskRepositoryIT.java`, `repository/NotificationRepositoryIT.java`, etc. |
| 21 — RecurrenceExpander | `TASK-21.NN` | `utils/RecurrenceExpanderTest.java` |
| 22 — FieldsValidator | `TASK-22.NN` | `utils/FieldsValidatorTest.java` |
| 23 — FileStorageService | `TASK-23.NN` | `service/FileStorageServiceTest.java` |
| 24 — Cross-service | `TASK-24.NN` | `integration/TaskE2EIntegrationTest.java` |
| 25 — Transversales | `TASK-25.NN` | `transversal/TaskI18nTest.java`, `transversal/TaskProblemDetailTest.java` |
| 26 — Seguridad | `TASK-26.NN` | `security/TaskSecurityTest.java` |
| 27 — Ownership 🚧 | `TASK-27.NN` | `ownership/TaskOwnershipTest.java` |

> Cada `@DisplayName` debe contener el ID. Ejemplo: `@DisplayName("TASK-1.01: happy path mínimo")`.

---

## 1. `POST /task` — creación de tarea (HU-TAR-01)

> **Endpoint:** `POST /task`. **Body:** `TaskRequest{task_type_code, terrain_id, planned_at, estimated_duration_minutes, assigned_to, planned_inputs?, notes?, recurrence?}`. **Validación:** Bean Validation + gRPC `CheckTerrainExists` + gRPC `ValidateUser` + lookup `task_type_code` + expansión de recurrencia. **Éxito:** 201 con `id` UUID.

| Caso | ID | Pre / Body | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path mínimo (sin recurrencia) | TASK-1.01 | body válido con `task_type_code=SOWING`, gRPC mocks → true | 201 + body con `id` | WebMvc + JDBC | ✅ (`post_validBody_returns201`) |
| Happy path completo | TASK-1.02 | + `planned_inputs`, `notes`, todos los campos | 201; JSONB `planned_inputs` persiste | WebMvc + JDBC | ❌ |
| `task_type_code` ausente | TASK-1.03 | sin campo | 400; `errors` contiene `task_type_code` | WebMvc | ❌ |
| `task_type_code` desconocido | TASK-1.04 | `"task_type_code":"NOPE"` | 400; `title:"Unknown task type"` o similar; `i18n: task.type.unknown` | WebMvc | ✅ (`createTask_unknownType_throws400`) |
| `terrain_id` ausente | TASK-1.05 | sin campo | 400; `errors` contiene `terrain_id` | WebMvc | ❌ |
| `terrain_id` no UUID | TASK-1.06 | `"terrain_id":"abc"` | 400 (Jackson) | WebMvc | ❌ |
| `terrain_id` inexistente | TASK-1.07 | mock `CheckTerrainExists → false` | 404; `title:"Terrain not found"` | WebMvc | ✅ (`post_terrainNotFound_returns404`, `createTask_terrainMissing_throws404`) |
| `planned_at` ausente | TASK-1.08 | sin campo | 400 | WebMvc | ❌ |
| `planned_at` en pasado | TASK-1.09 | `planned_at = now() - 1h` | 400; `i18n: task.planned.past` | WebMvc | ✅ (`post_pastDate_returns400`) |
| `planned_at` exactamente now (borde) | TASK-1.10 | `planned_at = now()` con tolerancia ms | hoy 400 (`@Future` estricto); documentar decisión | WebMvc | ❌ |
| `planned_at` formato erróneo | TASK-1.11 | `"01/01/2026"` | 400 (Jackson) | WebMvc | ❌ |
| `estimated_duration_minutes` ausente | TASK-1.12 | sin campo | 400 | WebMvc | ❌ |
| `estimated_duration_minutes` ≤ 0 | TASK-1.13 | `0` o `-5` | 400; constraint + `@Positive` | WebMvc | ❌ |
| `estimated_duration_minutes` muy grande | TASK-1.14 | `Integer.MAX_VALUE` | 201 (no hay límite superior); o documentar tope razonable | WebMvc | ❌ |
| `assigned_to` ausente | TASK-1.15 | sin campo | 400 | WebMvc | ❌ |
| `assigned_to` inexistente | TASK-1.16 | mock `ValidateUser → false` | 404; `title:"User not found"` | WebMvc + unit | ✅ (`createTask_assigneeMissing_throws404`) |
| Recurrencia WEEKLY x10 | TASK-1.17 | `recurrence={frequency:WEEKLY, interval:1, until:planned_at+70d}` | 201 + 10 filas hijas con `recurrence_parent_id` | WebMvc + JDBC | ✅ (`createTask_recurrenceWeeklyx10_inserts11rows`, parcial — verifica 11 filas; documentar) |
| Recurrencia DAILY x365 (borde) | TASK-1.18 | `frequency:DAILY, interval:1, until:planned_at+365d` | 201; instancias ≤ 365 | WebMvc + JDBC | ❌ |
| Recurrencia DAILY x366 | TASK-1.19 | until +366d | 400; `i18n: task.recurrence.too-many-instances` | WebMvc | ✅ (`createTask_recurrenceTooMany_throws`) |
| Recurrencia MONTHLY x12 | TASK-1.20 | `frequency:MONTHLY, interval:1, until:planned_at+365d` | 201 + 12 instancias | WebMvc + JDBC | ❌ |
| Recurrencia con `interval=0` | TASK-1.21 | inválido | 400 (`@Positive`) | WebMvc | ❌ |
| Recurrencia con `until < planned_at::date` | TASK-1.22 | until en pasado relativo | 201 con 0 instancias hijas — **decisión**: ¿400 o ignorar? Hoy: solo persiste la plantilla | WebMvc | ❌ |
| `planned_inputs` vacío | TASK-1.23 | `[]` | 201; JSONB = `[]` | WebMvc + JDBC | ❌ |
| `planned_inputs[].input_id` válido (input-service desplegado) | TASK-1.24 | mock `CheckInputExists → true` | 201 | WebMvc | 🚧 (depende de paquete 05) |
| `planned_inputs[].input_id` inexistente | TASK-1.25 | mock `CheckInputExists → false` | 404; `i18n: input.not.found` | WebMvc | 🚧 |
| `planned_inputs[].input_id` sin input-service desplegado | TASK-1.26 | no hay `grpc.client.input-service.address` configurado | 201; se omite validación | WebMvc | ❌ |
| `notes` con UTF-8 + emoji | TASK-1.27 | `"notes":"Riego sector norte 🌱"` | 201; round-trip preserva chars | JDBC | ❌ |
| `notes` > 5 MB | TASK-1.28 | string gigante | 413 o 400 (límite Spring) | WebMvc | ❌ |
| Body vacío | TASK-1.29 | `{}` | 400; `errors` con todos los campos obligatorios | WebMvc | ❌ |
| Body no JSON | TASK-1.30 | `Content-Type: text/plain` | 415 | WebMvc | ❌ |
| Body JSON malformado | TASK-1.31 | `'{"terrain_id":'` | 400 | WebMvc | ❌ |
| Orden de validación: terrain primero, después user, después type | TASK-1.32 | mocks todos false | excepción del primero (terrain) | unit | ❌ |
| Idempotencia: dos POST iguales | TASK-1.33 | mismo body válido x2 | 2 filas distintas (sin UNIQUE) | JDBC | ❌ |
| Concurrencia: 5 threads POST con mismo body | TASK-1.34 | mocks ok | 5 filas con UUIDs distintos | JDBC + concurrency | ❌ |
| `created_by` se infiere del JWT (no del body) | TASK-1.35 | header JWT con userId=X; body sin `created_by` | persistido con `created_by=X` | WebMvc + integración | 🚧 |
| `task-type_code` case-sensitive | TASK-1.36 | `"sowing"` minúscula | 400 (lookup distingue) o 201 si se normaliza — documentar | WebMvc | ❌ |

### 1.A Asserts adicionales en happy path

```java
List<Map<String,Object>> rows = jdbcTemplate.queryForList(
    "SELECT id, task_type_id, terrain_id, state, planned_inputs FROM task WHERE id = ?::uuid",
    returnedUuid);
assertThat(rows).hasSize(1);
assertThat(rows.get(0).get("state")).isEqualTo("PENDING");
```

---

## 2. `GET /task/{id}` — detalle con proyección

| Caso | ID | Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Detalle existente sin `fields` | TASK-2.01 | seed 1 fila | 200; mapa con todos los campos | WebMvc + JDBC | ✅ (`get_byId_returnsProjection`, parcial) |
| Detalle inexistente | TASK-2.02 | UUID aleatorio | 404; `title:"Task not found"`; `i18n: task.not.found` | WebMvc | ❌ |
| `fields=id,state` | TASK-2.03 | seed; GET con proyección | 200; mapa con solo 2 keys | WebMvc + JDBC | ❌ |
| `fields` con valor fuera de enum | TASK-2.04 | `?fields=secret` | 400; `i18n: task.field.unknown` | WebMvc | ❌ |
| `fields` mezcla válidos+inválidos | TASK-2.05 | `?fields=id,secret` | 400 | WebMvc | ❌ |
| `fields` duplicados | TASK-2.06 | `?fields=id,id,state` | 200 (deduplicado) | WebMvc | ❌ |
| `fields` vacío | TASK-2.07 | `?fields=` | 200 (equivale a `*`) | WebMvc | ❌ |
| Encoding UTF-8 en `notes` | TASK-2.08 | seed notes UTF-8 | 200; chars correctos | WebMvc + JDBC | ❌ |
| `id` no UUID | TASK-2.09 | `GET /task/abc` | 400 (binding) | WebMvc | ❌ |
| Detalle incluye `planned_inputs` y `consumed_inputs` JSONB | TASK-2.10 | seed con ambos | 200; ambos como array JSON | WebMvc + JDBC | ❌ |

---

## 3. `GET /task` — listado con filtros y paginación

| Caso | ID | Pre / Query | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Lista vacía | TASK-3.01 | sin filtros, BBDD vacía | 200; `Page` con `content=[]`, `totalElements=0` | WebMvc + JDBC | ❌ |
| Lista con paginación default | TASK-3.02 | seed 25 filas | 200; `content.length=20`, `totalElements=25`, `page=0`, `size=20` | WebMvc + JDBC | ✅ (`get_list_returnsPageResponse`, parcial) |
| Página 1 (segunda) | TASK-3.03 | seed 25; `?page=1&size=20` | 200; `content.length=5` | WebMvc + JDBC | ❌ |
| `size` muy grande | TASK-3.04 | `?size=10000` | 200; respeta el cap (`MAX_PAGE_SIZE=100` o 200; documentar) | WebMvc | ❌ |
| Filtro `assigned_to` | TASK-3.05 | seed mezclado; `?assigned_to=<X>` | 200; solo tareas asignadas a X | WebMvc + JDBC | ❌ |
| Filtro `created_by` | TASK-3.06 | similar | 200 | WebMvc + JDBC | ❌ |
| Filtro `state` CSV | TASK-3.07 | `?state=PENDING,IN_PROGRESS` | 200; estados filtrados | WebMvc + JDBC | ❌ |
| Filtro `state` con valor inválido | TASK-3.08 | `?state=NOPE` | 400 | WebMvc | ❌ |
| Filtro `task_type_code` CSV | TASK-3.09 | `?task_type_code=IRRIGATION,HARVEST` | 200; filtrado | WebMvc + JDBC | ❌ |
| Filtro `terrain_id` | TASK-3.10 | `?terrain_id=<T>` | 200; solo del terrain | WebMvc + JDBC | ❌ |
| Filtro `from`/`to` sobre `planned_at` | TASK-3.11 | `?from=2026-06-01&to=2026-07-01` | 200; solo en rango | WebMvc + JDBC | ❌ |
| Filtro `overdue=true` | TASK-3.12 | seed mezcla overdue/no | 200; SQL `state IN ('PENDING','IN_PROGRESS') AND (planned_at + duration) < NOW()` | WebMvc + JDBC | ❌ |
| Filtro `fields=id,state` | TASK-3.13 | proyección | 200; cada elemento con solo 2 keys | WebMvc + JDBC | ❌ |
| Filtros combinados | TASK-3.14 | `assigned_to + state + from/to` | AND lógico | WebMvc + JDBC | ❌ |
| Orden default por `planned_at ASC` | TASK-3.15 | seed | 200; orden correcto | JDBC | ❌ |
| Performance con 10k filas | TASK-3.16 | seed 10k | 200 < 1s con índice `idx_task_state_planned` | JDBC + perf | ❌ |

---

## 4. `GET /task/calendar` — vista calendario

| Caso | ID | Pre / Query | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path week | TASK-4.01 | `?view=week&from=A&to=A+7d` | 200; lista `TaskCalendarSlotDTO` | WebMvc + JDBC | ✅ (`calendar_validRange_returnsList`, parcial) |
| Happy path month | TASK-4.02 | `?view=month&from=A&to=A+31d` | 200 | WebMvc + JDBC | ❌ |
| Happy path year | TASK-4.03 | `?view=year&from=A&to=A+365d` | 200 | WebMvc + JDBC | ❌ |
| `view` inválido | TASK-4.04 | `?view=decade` | 400; `i18n: task.calendar.view.invalid` | WebMvc | ✅ (`calendar_invalidView_returns400`) |
| `view` ausente | TASK-4.05 | sin `view` | 400 o default week — documentar | WebMvc | ❌ |
| Rango > 13 meses | TASK-4.06 | `?view=year&from=A&to=A+14m` | 400; `i18n: task.calendar.range.too-wide` | WebMvc | ✅ (`calendar_rangeAboveOneYearPlusBuffer_throws`) |
| Rango = 0 (from==to) | TASK-4.07 | mismo timestamp | 200 con `[]` | WebMvc | ❌ |
| `from > to` | TASK-4.08 | invertido | 400 | WebMvc | ❌ |
| `color_hint` correcto por estado | TASK-4.09 | seed con PENDING/IN_PROGRESS/FINISHED/OVERDUE | colores correctos: azul/amarillo/verde/rojo | WebMvc + JDBC | ❌ |
| Densidad: tarea con duration=0 | TASK-4.10 | edge | 200; slot con duration mínima 1 min | WebMvc | ❌ |

---

## 5. `PATCH /task/{id}` — actualización

| Caso | ID | Pre / Body | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Update parcial OK | TASK-5.01 | body `{"notes":"..."}` | 200; otros campos intactos | WebMvc + JDBC | ❌ |
| Cambiar `planned_at` a futuro | TASK-5.02 | `{"planned_at":"...+5d"}` | 200 | WebMvc + JDBC | ❌ |
| Cambiar `planned_at` a pasado | TASK-5.03 | `{"planned_at":"...-1d"}` | 400 (mismo validador que POST) | WebMvc | ❌ |
| Cambiar `assigned_to` a user inexistente | TASK-5.04 | mock ValidateUser → false | 404 | WebMvc | ❌ |
| Cambiar `task_type_code` | TASK-5.05 | `{"task_type_code":"HARVEST"}` | 200 (busca en `task_type`) | WebMvc + JDBC | ❌ |
| Cambiar `task_type_code` desconocido | TASK-5.06 | `"NOPE"` | 400 | WebMvc | ❌ |
| Cambiar `state` directamente vía PATCH | TASK-5.07 | `{"state":"FINISHED"}` | 400 — `state` solo cambia vía `/transition` | WebMvc | ❌ |
| PATCH de task FINISHED | TASK-5.08 | task con `state=FINISHED` | 409; `i18n: task.finished.immutable` | WebMvc | ❌ |
| PATCH de task CANCELLED | TASK-5.09 | task con `state=CANCELLED` | 409 (mismo razón) | WebMvc | ❌ |
| `id` inexistente | TASK-5.10 | UUID aleatorio | 404 | WebMvc | ❌ |
| Body vacío `{}` | TASK-5.11 | `{}` | 200 (no-op) o 400 — documentar | WebMvc | ❌ |
| Propiedades extra Jackson | TASK-5.12 | `{"isAdmin":true}` | 200, ignorado por default | WebMvc | ❌ |
| `updated_at` se actualiza | TASK-5.13 | PATCH cualquiera | nuevo `updated_at` > anterior | JDBC | ❌ |

> Nota: si la implementación actual no soporta PATCH, marcar todos como 🚧 y planificarlo como issue.

---

## 6. `DELETE /task/{id}` — política de borrado

| Caso | ID | Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Happy path PENDING sin history | TASK-6.01 | task PENDING recién creada | 204; fila eliminada | WebMvc + JDBC | ✅ (`deleteTask_pendingWithoutHistory_ok`) |
| Task PENDING **con** history | TASK-6.02 | task con fila en `task_state_history` | 409; `i18n: task.cannot.delete.with.history` | WebMvc + JDBC | ✅ (`deleteTask_withHistory_conflict`, `delete_conflict_returns409`) |
| Task IN_PROGRESS | TASK-6.03 | task en progreso | 409 | WebMvc + JDBC | ✅ (`deleteTask_inProgress_conflict`) |
| Task FINISHED | TASK-6.04 | FINISHED | 409 | WebMvc | ❌ |
| Task CANCELLED | TASK-6.05 | CANCELLED | 409 | WebMvc | ❌ |
| `id` inexistente | TASK-6.06 | UUID aleatorio | 404 | WebMvc | ✅ (`deleteTask_notFound_throws`, `delete_notFound_returns404`) |
| `id` no UUID | TASK-6.07 | `DELETE /task/abc` | 400 (binding) | WebMvc | ❌ |
| Doble DELETE | TASK-6.08 | DELETE OK + DELETE | 204 + 404 | WebMvc + JDBC | ❌ |
| DELETE concurrente | TASK-6.09 | 2 threads, mismo id | uno 204, otro 404 (atomic) | JDBC + concurrency | ❌ |
| DELETE NO publica Kafka | TASK-6.10 | spy KafkaTemplate | sin invocaciones | unit | ❌ |
| DELETE de plantilla con hijas | TASK-6.11 | task recurrencia parent + hijas | hijas conservan `recurrence_parent_id=NULL` por `ON DELETE SET NULL` | JDBC | ❌ |
| Cascade limpia `task_state_history` y `task_evidence` | TASK-6.12 | (solo si DELETE permitido — eg force admin) | filas hijas borradas | JDBC | ❌ |

---

## 7. `POST /task/{id}/transition` — máquina de estados (HU-TAR-02)

### 7.1 Transiciones legales / ilegales

| Caso | ID | From → To | Resultado | Estado |
|---|---|---|---|---|
| PENDING → IN_PROGRESS | TASK-7.01 | legal | 200; fila history; estado actualizado | ✅ (`pendingToInProgress_ok_recordsHistory`) |
| PENDING → CANCELLED | TASK-7.02 | legal | 200; history | ❌ |
| PENDING → FINISHED (skip IN_PROGRESS) | TASK-7.03 | ilegal | 409; `i18n: task.transition.invalid` | ✅ (`pendingToFinished_isIllegal`) |
| IN_PROGRESS → FINISHED | TASK-7.04 | legal con duration+consumed | 200; publica `task-completed` | ✅ (`finishedTreatment_withEvidence_publishesEvent`, parcial) |
| IN_PROGRESS → CANCELLED | TASK-7.05 | legal | 200 | ❌ |
| FINISHED → cualquiera | TASK-7.06 | ilegal | 409; `i18n: task.finished.immutable` | ✅ (`finished_isImmutable`) |
| CANCELLED → cualquiera | TASK-7.07 | ilegal | 409 | ❌ |
| Loop: PENDING → IN_PROGRESS → PENDING | TASK-7.08 | ilegal | 409 | ❌ |

### 7.2 Reglas específicas de FINISHED

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| FINISHED sin `real_duration_minutes` | TASK-7.10 | body falta campo | 400; `i18n: task.duration.required` | ✅ (`finished_withoutRealDuration_throws`) |
| FINISHED sin `consumed_inputs` | TASK-7.11 | body falta campo | 400 | ✅ (`finished_withoutConsumedInputs_throws`) |
| FINISHED con `consumed_inputs=[]` | TASK-7.12 | array vacío | 400 (no vacío) o 200 — documentar; si se permite vacío, no hay descuento de stock | ❌ |
| FINISHED de TREATMENT sin evidencia | TASK-7.13 | task tipo TREATMENT sin `task_evidence` | 409; `i18n: task.evidence.required` | ✅ (`finishedTreatment_withoutEvidence_throws`) |
| FINISHED de FERTILIZATION sin evidencia | TASK-7.14 | mismo | 409 | ❌ |
| FINISHED de SOWING sin evidencia | TASK-7.15 | tipo NO obliga evidencia | 200 | ❌ |
| FINISHED de TREATMENT con ≥1 evidencia | TASK-7.16 | seed 1 evidence | 200 + publica Kafka | ✅ (`finishedTreatment_withEvidence_publishesEvent`) |

### 7.3 Side-effects

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Transición persiste fila en `task_state_history` | TASK-7.20 | cualquier legal | 1 fila con `from_state`, `to_state`, `changed_by`, `changed_at`, `note` | ✅ (`pendingToInProgress_ok_recordsHistory`) |
| `changed_by` se infiere del JWT | TASK-7.21 | header JWT userId=X | history.changed_by = X | 🚧 |
| FINISHED publica Kafka exactamente una vez | TASK-7.22 | spy KafkaTemplate | `send("task-completed",...)` invocado 1x | ✅ (parcial) |
| Payload `TaskCompletedEvent` correcto | TASK-7.23 | spy + captor | `taskId, taskTypeCode, terrainId, performedBy, finishedAt, consumedInputs` match | ❌ |
| Payload `TaskCompletedEvent` SIN `parcelId` | TASK-7.24 | inspección | el campo no existe (decisión D del plan) | ❌ |
| `effective_at` default = now() | TASK-7.25 | body sin `effective_at` | history.changed_at ≈ now() | ❌ |
| `effective_at` explícito | TASK-7.26 | body con timestamp | history.changed_at = ese | ❌ |
| `effective_at` futuro | TASK-7.27 | timestamp futuro | 400; `i18n: task.effective.past-or-present` (documentar política) | ❌ |
| Transición rechaza si publica Kafka falla | TASK-7.28 | mock template lanza | rollback total (no fila history, no estado actualizado) — depende del manejo transaccional | ❌ |
| `id` inexistente | TASK-7.29 | UUID random | 404 | ✅ (`taskNotFound_throws404`) |
| `to_state` inválido | TASK-7.30 | `"to_state":"NOPE"` | 400 | ❌ |
| Concurrencia: 2 threads transicionan misma task | TASK-7.31 | 2x POST simultáneo | uno gana, otro 409 (con optimistic locking) — documentar deuda actual | ❌ |

---

## 8. Endpoints de evidencias

> Multipart `POST /task/{id}/evidence`, `GET /task/{id}/evidence`, `GET /task/{id}/evidence/{evidenceId}/content`, `DELETE /task/{id}/evidence/{evidenceId}`.

| Caso | ID | Pre / Body | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| POST happy path PNG < 10 MB | TASK-8.01 | multipart, `image/png`, 5 MB | 201; fila `task_evidence`; archivo en `${attachments.storage.root}` | WebMvc + JDBC + storage | ❌ |
| POST JPG | TASK-8.02 | `image/jpeg` | 201 | WebMvc | ❌ |
| POST PDF | TASK-8.03 | `application/pdf` | 201 | WebMvc | ❌ |
| POST MIME no permitido | TASK-8.04 | `text/plain` | 400; `i18n: task.evidence.mime.invalid` | WebMvc | ❌ |
| POST > 10 MB | TASK-8.05 | archivo 11 MB | 413 (MaxUploadSizeExceeded) o 400 | WebMvc | ❌ |
| POST con `task_id` inexistente | TASK-8.06 | task no existe | 404 | WebMvc | ❌ |
| POST a task FINISHED | TASK-8.07 | task FINISHED | 409 o 200 — documentar política | WebMvc | ❌ |
| GET listado de evidencias | TASK-8.08 | seed 3 evidencias | 200; array con metadatos | WebMvc + JDBC | ❌ |
| GET content devuelve bytes con MIME correcto | TASK-8.09 | seed 1 PNG | 200; `Content-Type: image/png`; bytes idénticos | WebMvc + storage | ❌ |
| GET content de evidencia inexistente | TASK-8.10 | id random | 404 | WebMvc | ❌ |
| DELETE evidencia | TASK-8.11 | seed 1 | 204; fila + archivo borrados | WebMvc + JDBC + storage | ❌ |
| DELETE de evidencia inexistente | TASK-8.12 | random | 404 | WebMvc | ❌ |
| Filename con path traversal | TASK-8.13 | `original_name="../../../etc/passwd"` | rechazado o saneado; storage NO escribe fuera del root | service | ❌ |
| MIME spoofing: extensión .png pero content malformado | TASK-8.14 | bytes basura con header png | rechazado por magic-number check (si implementado) | service | ❌ |
| Subir 2 archivos con mismo `original_name` | TASK-8.15 | 2 POST iguales | 2 filas con `storage_key` distinta (UUID) | JDBC | ❌ |
| Cascade DELETE task → task_evidence | TASK-8.16 | DELETE task con evidencias | evidencias borradas; archivos huérfanos pendientes (deuda conocida) | JDBC | ❌ |

---

## 9. `GET /task/dashboard` (HU-TAR-03)

| Caso | ID | Pre / Filtros | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Dashboard vacío | TASK-9.01 | BBDD vacía | 200; totals todos 0; arrays vacíos | WebMvc + JDBC | ❌ |
| Totals correctos | TASK-9.02 | seed mezcla estados | 200; conteos exactos por `state` + OVERDUE | WebMvc + JDBC | ❌ |
| `by_week` agrupa correctamente | TASK-9.03 | seed con planned_at distribuido | 200; agrupa semanal | JDBC | ❌ |
| `by_type` agrupa correctamente | TASK-9.04 | seed mezcla tipos | 200; agrupa por `task_type_code` | JDBC | ❌ |
| Dashboard scope agricultor | TASK-9.05 | rol agricultor; X tiene 3 terrenos | totals solo de sus 3 terrenos | integración | ❌ |
| Dashboard scope técnico | TASK-9.06 | rol técnico; X asignado a 5 tareas | totals solo de las suyas | integración | ❌ |
| Dashboard scope admin | TASK-9.07 | rol admin | totals globales | integración | ❌ |
| OVERDUE: incluye PENDING e IN_PROGRESS | TASK-9.08 | seed con duration+planned_at < now | overdue cuenta correctamente | JDBC | ❌ |
| OVERDUE: excluye FINISHED y CANCELLED | TASK-9.09 | seed con FINISHED vencidos | NO se cuentan | JDBC | ❌ |

---

## 10. `GET /task/export.csv` — exportación

| Caso | ID | Pre / Filtros | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| Export vacío | TASK-10.01 | sin filas | 200; headers CSV + cuerpo solo cabecera | WebMvc | ❌ |
| Export con 100 filas | TASK-10.02 | seed 100 | 200; `Content-Type: text/csv`; 101 líneas | WebMvc | ✅ (`TaskExportServiceTest` cubre patrón) |
| Export con 10k filas (streaming) | TASK-10.03 | mock | respuesta streamed; sin OOM | service | ✅ (parcial, en `TaskExportServiceTest`) |
| Aplica mismos filtros que `GET /task` | TASK-10.04 | `?state=PENDING&assigned_to=X` | CSV filtrado | WebMvc | ❌ |
| Filtra por rol (agricultor solo sus terrenos) | TASK-10.05 | rol agricultor | CSV con sus filas | integración | ❌ |
| Columnas correctas | TASK-10.06 | inspección de header CSV | `id, task_type_code, terrain_id, planned_at, state, started_at, finished_at, real_duration_minutes, assigned_to, created_by` | WebMvc | ❌ |
| Escape de chars CSV (comas, comillas, newlines en `notes`) | TASK-10.07 | seed notes con `,"\n` | CSV bien formado (RFC 4180) | service | ❌ |
| Headers HTTP correctos | TASK-10.08 | inspección | `Content-Disposition: attachment; filename=tasks.csv` | WebMvc | ❌ |
| UTF-8 BOM | TASK-10.09 | inspección bytes | con/sin BOM — documentar decisión Excel-friendly | service | ❌ |

---

## 11. Role scoping (admin / agricultor / técnico)

> Lógica en `RoleScopingService` que decide la cláusula `WHERE` adicional según el rol del JWT.

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| admin: ve todo | TASK-11.01 | rol admin | sin WHERE adicional | ✅ (`RoleScopingServiceTest`) |
| agricultor: solo sus terrenos | TASK-11.02 | rol agricultor; REST a terrain-service devuelve [T1,T2] | WHERE `terrain_id IN (T1,T2)` | ✅ |
| agricultor sin terrenos | TASK-11.03 | terrain-service devuelve [] | WHERE `terrain_id IN (NULL)` → 0 filas | ✅ |
| técnico: solo asignadas a él | TASK-11.04 | rol técnico, userId=X | WHERE `assigned_to=X` | ✅ |
| Llamada REST terrain-service falla | TASK-11.05 | timeout/5xx | 500 propagado o degradado a 0 filas — documentar | ❌ |
| Cache local de terrain_ids (opcional) | TASK-11.06 | mismas 2 llamadas seguidas | la 2ª usa cache | 🟡 |
| JWT sin claim `role` | TASK-11.07 | malformado | 403 o 401 — documentar | ❌ |
| JWT con rol desconocido | TASK-11.08 | `role="hacker"` | 403 | ❌ |

---

## 12. Endpoints de notificaciones (HU-TAR-04)

| Caso | ID | Pre / Request | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| `GET /notification` bandeja vacía | TASK-12.01 | user sin notifs | 200; Page `content=[]` | WebMvc + JDBC | ❌ |
| `GET /notification` paginado | TASK-12.02 | seed 30; `?size=10&page=1` | 200; `content.length=10` | WebMvc + JDBC | ❌ |
| Orden `created_at DESC` | TASK-12.03 | seed con timestamps | orden correcto | JDBC | ❌ |
| Filtro `unread=true` | TASK-12.04 | seed mixto | solo `read_at IS NULL` | WebMvc + JDBC | ❌ |
| `POST /notification/{id}/read` happy | TASK-12.05 | notif del user | 200; `read_at = now()` | WebMvc + JDBC | ❌ |
| `POST /notification/{id}/read` de otro user | TASK-12.06 | notif de otro user | 404 (mejor que 403 — no filtrar existencia) | WebMvc | ❌ |
| `POST /notification/{id}/read` ya leída | TASK-12.07 | `read_at` ya seteado | 200 idempotente (no cambia timestamp) | WebMvc + JDBC | ❌ |
| `POST /notification/mark-all-read` | TASK-12.08 | 5 notifs unread | 200; todas leídas | WebMvc + JDBC | ❌ |
| `GET /notification/unread-count` | TASK-12.09 | seed mixto | 200; `{count: N}` | WebMvc + JDBC | ❌ |
| `GET /notification/preferences` (inexistente) | TASK-12.10 | user sin pref guardadas | 200; defaults (`email_enabled=true, default_lead_minutes=1440`) | WebMvc | ✅ (`getPreferences_emptyReturnsDefault`) |
| `PUT /notification/preferences` | TASK-12.11 | body con preferencias | 200; persistidas | WebMvc + JDBC | ❌ |
| PUT con `task_type_lead_minutes` JSONB | TASK-12.12 | `{"TREATMENT":2880}` | 200; serializado bien | JDBC | ✅ (`upsertPreferences_serializesTypeMap`) |
| PUT con `quiet_hours_start > quiet_hours_end` | TASK-12.13 | 22:00 → 07:00 (cross-midnight) | 200; semántica wrap-around | WebMvc + service | ✅ (`quietHours_crossingMidnight`) |
| PUT con valor inválido (`default_lead_minutes < 0`) | TASK-12.14 | -10 | 400 | WebMvc | ❌ |
| `id` no UUID | TASK-12.15 | `POST /notification/abc/read` | 400 | WebMvc | ❌ |

---

## 13. `NotificationSchedulerService` — UPCOMING / OVERDUE / DIGEST

> `@Scheduled(fixedDelay = 60_000)`. En tests, invocar `scheduler.runScan()` directamente.

| Caso | ID | Pre | Resultado | Capa | Estado |
|---|---|---|---|---|---|
| UPCOMING crea notif | TASK-13.01 | task PENDING, planned_at = now+23h, lead=1440min | 1 fila notif `source_kind=TASK_UPCOMING` | unit + JDBC | ✅ (`NotificationSchedulerServiceTest`) |
| UPCOMING dedupe | TASK-13.02 | scan x2; mismo task+user | solo 1 fila notif | unit + JDBC | ✅ (`createUpcoming_dedup`) |
| UPCOMING fuera de ventana | TASK-13.03 | planned_at = now+48h, lead=1440 | 0 filas (aún no toca) | unit + JDBC | ❌ |
| UPCOMING respeta `task_type_lead_minutes` override | TASK-13.04 | TREATMENT con lead override=2880; planned_at=now+47h | 1 fila notif | unit | ✅ (`resolveLead_perTypeOverride`) |
| UPCOMING NO emite si task ya IN_PROGRESS | TASK-13.05 | task en progreso | 0 filas (solo PENDING) | unit | ❌ |
| OVERDUE crea notif | TASK-13.06 | task overdue, sin OVERDUE notif del día | 1 fila | unit + JDBC | ✅ |
| OVERDUE dedupe por día | TASK-13.07 | scan x2 mismo día | 1 fila (no 2) | unit + JDBC | ❌ |
| OVERDUE re-emite al día siguiente | TASK-13.08 | scan +24h | nueva fila OVERDUE | JDBC | ❌ |
| OVERDUE digest > 10 | TASK-13.09 | 11 tasks overdue | 1 fila DIGEST + suprime 11 individuales | unit + JDBC | ✅ (`createOverdue_above10_collapsesToDigest`) |
| Quiet hours respect EMAIL | TASK-13.10 | preferencia 22:00-07:00; scan a las 23:00 | NO se envía email; SÍ in-app | unit | ✅ (`quietHours_crossingMidnight`) |
| Quiet hours wrap-around | TASK-13.11 | 22:00-07:00, scan a las 03:00 | NO email | unit | ✅ |
| `also_notify_creator=true` | TASK-13.12 | task con `assigned_to=A, created_by=B`, pref B `also_notify_creator=true` | 2 filas (una por user) | unit + JDBC | ❌ |
| Scheduler robusto ante BBDD caída | TASK-13.13 | mock repo lanza | log error + siguiente iteración recupera | unit | ❌ |
| Performance: 1000 tasks PENDING | TASK-13.14 | seed 1000 | scan < 2s | perf | ❌ |
| MailService no configurado | TASK-13.15 | sin SMTP env vars | log warn + sigue in-app | unit | ❌ |

---

## 14. gRPC clients (UserGrpcClient, TerrainGrpcClient)

### 14.1 `UserGrpcClient`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `validateUser` happy → true | TASK-14.01 | stub `exists=true` | método → true | ❌ |
| `validateUser` happy → false | TASK-14.02 | stub `exists=false` | método → false | ❌ |
| `validateUser` UNAVAILABLE | TASK-14.03 | `StatusRuntimeException(UNAVAILABLE)` | envuelve en `RuntimeException` con mensaje | ❌ |
| `validateUser` UUID null | TASK-14.04 | input null | hoy NPE; documentar | ❌ |
| `validateUser` UUID malformado en el stub | TASK-14.05 | servidor remoto rechaza | (cubierto a nivel server, no client) | ❌ |
| Latencia: deadline configurable | TASK-14.06 | stub bloquea > deadline | si se añade, timeout | 🟡 |

### 14.2 `TerrainGrpcClient`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `checkTerrainExists` true | TASK-14.10 | stub `exists=true` | método → true | ❌ |
| `checkTerrainExists` false | TASK-14.11 | stub `exists=false` | método → false | ❌ |
| `checkTerrainExists` UNAVAILABLE | TASK-14.12 | error gRPC | wrap RuntimeException | ❌ |

### 14.3 Contract tests

| Caso | ID | Verificación | Estado |
|---|---|---|---|
| `terrain.proto` coincide con el de terrain-service | TASK-14.20 | hash o golden snapshot | ❌ |
| `validate_user.proto` coincide con el de auth-service | TASK-14.21 | hash o golden snapshot | ❌ |
| Stubs compilan | TASK-14.22 | `./mvnw -pl task-service compile` | ✅ (verificado en build) |

---

## 15. Listener `user-deleted` — política D2

### 15.1 Unit del listener

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Listener delega al service | TASK-15.01 | mock service; invocar listener | `handleUserDeleted(userId)` invocado 1x | ✅ (`UserDeletedListenerTest`) |
| Excepción service propaga | TASK-15.02 | mock lanza | propaga (Spring Kafka reintentará) | ❌ |
| `userId` nulo | TASK-15.03 | event con null | delega tal cual; SQL borra 0 filas | ❌ |

### 15.2 Política D2 (en `TaskService.handleUserDeleted`)

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Solo PENDING/IN_PROGRESS/CANCELLED se DELETE | TASK-15.10 | seed 4 tasks una de cada estado | 3 borradas (PENDING, IN_PROGRESS, CANCELLED) | ✅ (`TaskServiceUserDeletedTest`, parcial) |
| FINISHED se anonymiza (assigned_to → UUID-cero) | TASK-15.11 | seed FINISHED con assigned_to=X | fila persiste, `assigned_to=00000…` | ✅ (parcial) |
| FINISHED anonymiza también `created_by` | TASK-15.12 | seed FINISHED con created_by=X | `created_by=00000…` | ✅ (parcial) |
| `notification` y `notification_preference` del user borradas | TASK-15.13 | seed | ambas tablas vacías post-listener | ✅ (`deleteByUserId_deletesNotifsAndPrefs`) |
| `notification_emission_log` del user borrado | TASK-15.14 | seed | filas borradas | ❌ |
| Sin tasks del user | TASK-15.15 | BBDD vacía para X | summary (0,0,0); no excepción | ❌ |
| User es solo `created_by` (no assignee) en FINISHED | TASK-15.16 | seed | `created_by` anonymized, `assigned_to` intacto | ❌ |
| User en CANCELLED es borrado, no anonymized | TASK-15.17 | seed CANCELLED | borrado | ❌ |
| Concurrencia: 2 eventos del mismo userId | TASK-15.18 | publicar x2 | idempotente — segundo affect=0 | JDBC | ❌ |
| Listener integración con `@EmbeddedKafka` | TASK-15.19 | publish event vía template | tras 1s, tasks PENDING borradas, FINISHED anonymized | ❌ |

---

## 16. Listener `terrain-deleted` — cascada

### 16.1 Unit

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Listener delega | TASK-16.01 | mock service | `deleteByTerrainId(terrainId)` invocado | ✅ (`TerrainDeletedListenerTest`) |
| `terrainId` nulo | TASK-16.02 | event null | delega; SQL borra 0 | ❌ |

### 16.2 Integración

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| DELETE todas las tasks del terrain | TASK-16.10 | seed 5 tasks del terrain T | tras evento, 0 tasks de T; otras intactas | `@EmbeddedKafka` + JDBC | ❌ |
| Cascade `task_state_history` y `task_evidence` | TASK-16.11 | seed con history+evidence | filas hijas borradas (ON DELETE CASCADE SQL) | JDBC | ❌ |
| `notification` con `task_id` borrado por cascade | TASK-16.12 | seed notif con task_id | borrada (FK CASCADE) | JDBC | ❌ |
| Mensaje con `__TypeId__` correcto | TASK-16.13 | publish con header `com.agro.terrainservice.event.TerrainDeletedEvent` | listener procesa | `@EmbeddedKafka` | ❌ |
| Mensaje sin `__TypeId__` | TASK-16.14 | publish sin header | falla deserialización; documentar | ❌ |
| Idempotencia: mismo evento 2x | TASK-16.15 | publish x2 | 2ª no afecta filas | JDBC + Kafka | ❌ |

---

## 17. Listener `stock-low` — hub D5

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Listener delega al NotificationService | TASK-17.01 | mock | `createFromStockLow(event)` invocado | ✅ (`StockLowListenerTest`) |
| Primera alerta crea notif | TASK-17.02 | sin emisión previa | 1 fila `notification(source_kind=STOCK_LOW, source_ref=inputId, user_id=createdBy)` + log emission | ✅ (`createFromStockLow_first_creates`) |
| Dedup 24h: 2ª alerta no crea | TASK-17.03 | emisión hace 1h | 0 filas nuevas | ✅ (`createFromStockLow_dedupWithin24h`) |
| Dedup expira tras 24h | TASK-17.04 | emisión hace 25h | nueva fila | ❌ |
| Subject/body desde i18n | TASK-17.05 | inspección | claves resueltas con args | ❌ |
| Multilenguaje según preferencia user (futuro) | TASK-17.06 | pref user es ES | mensaje ES | 🚧 |
| Email + in-app si pref habilitada | TASK-17.07 | preferencia ambas activas | MailService.send invocado | ❌ |
| Solo in-app si email_enabled=false | TASK-17.08 | preferencia email_enabled=false | mail NO invocado | ❌ |
| `createdBy` ausente en payload | TASK-17.09 | event con createdBy=null | log warn; no crea notif | ❌ |
| `inputId` nulo | TASK-17.10 | event con inputId=null | log warn | ❌ |
| Integración `@EmbeddedKafka` | TASK-17.11 | publish con `__TypeId__=com.agro.inputservice.event.StockLowEvent` | tras 1s, fila notif | ❌ |
| Type-mapping correcto | TASK-17.12 | inspección properties | `com.agro.inputservice.event.StockLowEvent:com.agro.taskservice.event.StockLowEvent` | smoke | ✅ |

---

## 18. Listener `sensor-alert` — hub D5

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Listener delega | TASK-18.01 | mock | `createFromSensorAlert(event)` invocado | ✅ (`SensorAlertListenerTest`) |
| 1 alerta → N notifs (una por destinatario) | TASK-18.02 | notifyUserIds=[A,B,C] | 3 filas notification | ✅ |
| Agrupa si > 5 alertas mismo sensor / 1h | TASK-18.03 | 6 alertas mismo sensorId / mismo user | 1 fila "N alertas en sensor X" en lugar de 6 individuales | ✅ (`createFromSensorAlert_groupsAbove5_perHour`) |
| Grupo expira tras 1h | TASK-18.04 | nueva alerta tras 61min | crea notif individual de nuevo | ❌ |
| Subject/body i18n por kind (below/above) | TASK-18.05 | event con kind=BELOW_MIN o ABOVE_MAX | clave correcta resuelta | ❌ |
| `notifyUserIds` vacío | TASK-18.06 | array vacío | 0 notifs; no error | ❌ |
| `notifyUserIds` con user inexistente | TASK-18.07 | userId no en auth | crea notif igualmente (no validamos en hub) | ❌ |
| Payload sin `terrainId` (legacy parcelId) | TASK-18.08 | evento con `parcelId` (hipotético cliente viejo) | rechazado o ignorado — documentar | ❌ |
| Concurrencia: 2 alertas mismo sensor llegan en paralelo | TASK-18.09 | publish x2 | ambas procesadas (transacciones aisladas) | ❌ |
| Integración `@EmbeddedKafka` | TASK-18.10 | publish con `__TypeId__=com.agro.iotservice.event.SensorAlertEvent` | tras 1s, filas notif | ❌ |

---

## 19. Producer Kafka `task-completed`

### 19.1 Unit del `EventPublisher`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `publishTaskCompleted` llama a `KafkaTemplate.send` | TASK-19.01 | mock template | invocado con `topic="task-completed"`, key=taskId, value=event | ❌ |
| Payload completo | TASK-19.02 | captor | `taskId, taskTypeCode, terrainId, performedBy, finishedAt, consumedInputs` | ❌ |
| Payload sin `parcelId` | TASK-19.03 | inspección record | el campo no existe | ❌ |

### 19.2 Integración `@EmbeddedKafka`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Publish + consume por mock | TASK-19.10 | publish; consumer mock en el mismo topic | recibe el mensaje con `__TypeId__` correcto | ❌ |
| Header `__TypeId__` se serializa con `JsonSerializer` | TASK-19.11 | inspección record headers | `com.agro.taskservice.event.TaskCompletedEvent` | ❌ |
| Falla del broker → producer NO bloquea el commit | TASK-19.12 | parar broker mid-test | la transacción local commit y la publicación se reintenta — documentar política `acks` | ❌ |

---

## 20. Repositorio (BBDD real con Testcontainers)

> `@JdbcTest + @Import(<Repo>.class) + @Testcontainers`. Verifica SQL, mapeos, constraints, JSONB.

### 20.1 `TaskRepository`

| Caso | ID | Operación | Resultado | Estado |
|---|---|---|---|---|
| Flyway V1+V2+V3 aplica limpio | TASK-20.01 | `select * from flyway_schema_history` | 3 rows; `task_type` con 6 seeds | ❌ |
| INSERT happy | TASK-20.02 | todos los campos | UUID generado | ❌ |
| INSERT con `task_type_id` inexistente | TASK-20.03 | id=99 | DataIntegrityViolation | ❌ |
| Constraint `estimated_duration_minutes > 0` | TASK-20.04 | 0 o -5 | violation | ❌ |
| Constraint `state IN (...)` | TASK-20.05 | state='WEIRD' | violation | ❌ |
| Constraint `task_finished_after_started` | TASK-20.06 | finished_at < started_at | violation | ❌ |
| JSONB `planned_inputs` round-trip | TASK-20.07 | insert + select | mismo JSON | ❌ |
| JSONB con caracteres especiales | TASK-20.08 | `{"name":"O'Higgins"}` | round-trip OK | ❌ |
| Índice `idx_task_state_planned` usado | TASK-20.09 | EXPLAIN query con WHERE state+planned_at | usa index scan | perf | ❌ |
| `recurrence_parent_id` ON DELETE SET NULL | TASK-20.10 | borrar parent | hijas con `recurrence_parent_id=NULL` | ❌ |
| `task_state_history` cascade ON DELETE | TASK-20.11 | borrar task con history | history vacío | ❌ |

### 20.2 `NotificationRepository`

| Caso | ID | Operación | Resultado | Estado |
|---|---|---|---|---|
| INSERT con `task_id=null` (D5) | TASK-20.20 | source_kind=STOCK_LOW, task_id=null | OK | ❌ |
| INSERT con `task_id` real | TASK-20.21 | OK | ❌ |
| Cascade DELETE notif al borrar task | TASK-20.22 | DELETE task | notifs borradas | ❌ |
| Índice partial `idx_notification_user_unread` | TASK-20.23 | EXPLAIN | usa index | perf | ❌ |

### 20.3 `NotificationEmissionLogRepository`

| Caso | ID | Operación | Resultado | Estado |
|---|---|---|---|---|
| UNIQUE (source_kind, source_ref, user_id, emitted_at) | TASK-20.30 | 2 inserts mismo timestamp ms | el 2º falla | ❌ |
| Lookup recent emissions | TASK-20.31 | seed varias | retorna últimas N | ❌ |

### 20.A Configuración Testcontainers

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

Validar: `SELECT COUNT(*) FROM flyway_schema_history >= 3` y `SELECT COUNT(*) FROM task_type == 6`.

---

## 21. `RecurrenceExpander`

| Caso | ID | Input | Resultado | Estado |
|---|---|---|---|---|
| DAILY x7 | TASK-21.01 | freq=DAILY, interval=1, until=+7d | 7 instancias | ✅ (`RecurrenceExpanderTest`) |
| WEEKLY x4 | TASK-21.02 | freq=WEEKLY, interval=1, until=+28d | 4 instancias | ✅ |
| MONTHLY x12 | TASK-21.03 | freq=MONTHLY, interval=1, until=+365d | 12 instancias | ✅ |
| DAILY interval=2 (cada 2 días) | TASK-21.04 | freq=DAILY, interval=2, until=+10d | 5 instancias | ✅ |
| until antes de start | TASK-21.05 | until = start-1d | 0 instancias | ✅ |
| until == start | TASK-21.06 | borde | 0 instancias o 1 — documentar; hoy: 0 | ❌ |
| Límite 365 | TASK-21.07 | freq=DAILY, until=+400d | excepción `task.recurrence.too-many-instances` | ❌ |
| Frequency null | TASK-21.08 | null | NPE o validación previa | ❌ |
| Cambio de horario verano/invierno (DST) | TASK-21.09 | DAILY que cruza marzo/octubre | siguiente instancia conserva hora local | ❌ |

---

## 22. `FieldsValidator`

| Caso | ID | Input | Resultado | Estado |
|---|---|---|---|---|
| Lista vacía → `*` | TASK-22.01 | `null` o `[]` | `SELECT *` | ❌ |
| Campos válidos → `id, state` | TASK-22.02 | `["id","state"]` | string `"id, state"` | ❌ |
| Campo inválido | TASK-22.03 | `["secret"]` | `InvalidFieldException` con i18n | ❌ |
| Mezcla válidos+inválidos | TASK-22.04 | `["id","secret"]` | excepción al primero inválido | ❌ |
| Duplicados | TASK-22.05 | `["id","id"]` | dedup → `"id"` | ❌ |
| Tratamiento especial JSONB | TASK-22.06 | `["planned_inputs"]` | `"planned_inputs"` (Postgres serializa) | ❌ |
| Protección SQLi | TASK-22.07 | `["id; DROP TABLE task;"]` | excepción (no llega al SQL) | ❌ |

---

## 23. `FileStorageService`

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| `save` devuelve storage_key UUID | TASK-23.01 | bytes válidos | key generada; archivo en root | ❌ |
| `read` devuelve los mismos bytes | TASK-23.02 | save + read | bytes idénticos | ❌ |
| `delete` borra archivo | TASK-23.03 | save + delete | archivo no existe | ❌ |
| Path traversal en `original_name` | TASK-23.04 | `"../../etc/passwd"` | guardado bajo root con key sanitizada | ❌ |
| Directorio root no existe | TASK-23.05 | root no creado | service crea o lanza | ❌ |
| Disk full simulation | TASK-23.06 | mock IOException | propaga con i18n | ❌ |
| Concurrencia: 2 saves en paralelo | TASK-23.07 | 2 threads | 2 archivos distintos sin colisión de key | ❌ |

---

## 24. Integración cross-service

### 24.1 POST /task → cascada gRPC + Kafka

| Caso | ID | Mock terrain | Mock user | Resultado | Estado |
|---|---|---|---|---|---|
| Ambos true | TASK-24.01 | true | true | 201 + INSERT | ❌ |
| Terrain false | TASK-24.02 | false | true | 404; sin INSERT; user NO llamado | ❌ |
| User false | TASK-24.03 | true | false | 404; sin INSERT | ❌ |
| Terrain UNAVAILABLE | TASK-24.04 | error | n/a | 500 + sin INSERT | ❌ |

### 24.2 Saga end-to-end: terrain-deleted → cascade tasks

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| Recibir terrain-deleted borra todas las tasks del terrain | TASK-24.10 | seed 5 tasks de T; publish | tras 1s, 0 tasks de T | `@EmbeddedKafka` + JDBC | ❌ |
| Cascade task_evidence + task_state_history | TASK-24.11 | seed con dependencias | hijas borradas | JDBC | ❌ |
| Otras tasks intactas | TASK-24.12 | seed mezcla T1/T2 | solo T1 limpia | JDBC | ❌ |

### 24.3 Saga end-to-end: user-deleted → política D2

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| user-deleted: tasks PENDING borradas, FINISHED anonymized | TASK-24.20 | seed mezcla 4 estados | tras 1s: solo FINISHED queda con `assigned_to=00000…` | `@EmbeddedKafka` + JDBC | ❌ |

### 24.4 Hub D5 end-to-end

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| stock-low → notification creada | TASK-24.30 | publish event con createdBy=X | tras 1s, fila notification para X | `@EmbeddedKafka` + JDBC | ❌ |
| sensor-alert → N notifications | TASK-24.31 | publish con notifyUserIds=[A,B] | tras 1s, 2 filas | `@EmbeddedKafka` + JDBC | ❌ |

### 24.5 Smoke completo con stack real

| Caso | ID | Pre | Resultado | Estado |
|---|---|---|---|---|
| docker compose up + curl flujo POST/transition | TASK-24.40 | stack completo | E2E manual OK; `kcat -t task-completed` muestra evento | manual | ❌ |

---

## 25. Transversales (i18n, ProblemDetail, locales, headers)

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| Locale default ES | TASK-25.01 | sin `Accept-Language` | mensajes ES | ❌ |
| Locale EN | TASK-25.02 | `Accept-Language: en` | mensajes EN | ❌ |
| Locale desconocido | TASK-25.03 | `zh` | fallback EN | ❌ |
| Multi-locale | TASK-25.04 | `zh, es;q=0.5` | resuelve a ES o EN | ❌ |
| `Content-Type: application/problem+json` en errores | TASK-25.05 | cualquier 4xx | header correcto | ❌ |
| `ProblemDetail` campos mínimos | TASK-25.06 | inspección JSON | `type, title, status, detail` | ❌ |
| `errors[]` en 400 validación | TASK-25.07 | request inválido | array con `<campo>: <mensaje>` | ❌ |
| UTF-8 charset | TASK-25.08 | response | `charset=UTF-8` | ❌ |
| Args interpolados en mensaje | TASK-25.09 | `{0}` reemplazado por UUID | mensaje completo | ❌ |
| Mensaje de validación con regex | TASK-25.10 | DTO con regex | mensaje correcto | ❌ |

---

## 26. Seguridad defensiva

| Caso | ID | Escenario | Resultado | Estado |
|---|---|---|---|---|
| SQLi vía `fields` | TASK-26.01 | `?fields=id;DROP TABLE task;--` | 400 binding | ❌ |
| SQLi vía path id | TASK-26.02 | `/task/abc OR 1=1` | 400 | ❌ |
| Body propiedades extra | TASK-26.03 | `{"isAdmin":true}` | 201 sin persistir | ❌ |
| Tipos inesperados Jackson | TASK-26.04 | `"planned_at":12345` | 400 | ❌ |
| Payload gigante | TASK-26.05 | body 5 MB | 413 o 400 | ❌ |
| Path traversal | TASK-26.06 | `/task/../../etc/passwd` | 400 binding | ❌ |
| Header injection | TASK-26.07 | `Accept-Language: es\r\nX-Bad: 1` | rechazado por servlet | ❌ |
| Auth bypass directo al servicio | TASK-26.08 | request a `:8084/task` sin JWT | hoy 200 (validación está en gateway). Documentado. | ❌ |
| Rate-limit | TASK-26.09 | 1000 POST en 1s | sin rate-limit; mejora futura | 🟡 |
| File upload: tipo MIME spoofed | TASK-26.10 | png mal-formado | rechazado por magic-number (si implementado) | 🟡 |
| File upload: zip-bomb | TASK-26.11 | archivo `.pdf` con compresión 1:1000 | rechazado por tamaño post-decompression | 🚧 |
| JWT con `role` admin falsificado | TASK-26.12 | token firmado con secret distinto | 401 (gateway lo rechaza) | integración | ❌ |
| DoS por scheduler | TASK-26.13 | 1M tasks PENDING; scan | sin OOM (limit batch) | perf | 🚧 |

---

## 27. Bloque ownership 🚧 (futuro)

> Toda esta sección depende de implementar primero la validación de propiedad cross-service descrita en `MICROSERVICES-RELATIONSHIPS.md` §7.4 y `INVESTIGACION-RELACION-TERRAIN-SEASON-CROP.md` §4. Objetivo: cada endpoint verifica que el terreno referenciado pertenece al solicitante.

### 27.1 Prerequisito de implementación

- `terrain-service` expone RPC `CheckTerrainOwnership(terrain_id, user_id) → {exists, owned_by_user}`.
- `api-gateway` decodifica el JWT y propaga `X-User-Id` al downstream.
- `task-service` recibe `@RequestHeader("X-User-Id") UUID userId` en cada controller method.

### 27.2 Casos del bloque 🚧

| Caso | ID | Pre / Request | Resultado | Estado |
|---|---|---|---|---|
| POST /task sin `X-User-Id` | TASK-27.01 | cabecera ausente | 400 | 🚧 |
| POST /task con terrain ajeno | TASK-27.02 | `owned=false` | 403; `i18n: terrain.not.owned` | 🚧 |
| POST /task con terrain propio | TASK-27.03 | `owned=true` | 201 | 🚧 |
| POST /task con terrain inexistente | TASK-27.04 | `exists=false` | 404 | 🚧 |
| GET /task/{id} de otro user | TASK-27.05 | task de B, request de A | 404 (no filtrar existencia) | 🚧 |
| DELETE /task/{id} de otro user | TASK-27.06 | similar | 404 | 🚧 |
| Transition de task ajena | TASK-27.07 | similar | 404 | 🚧 |
| Dashboard scope correcto | TASK-27.08 | rol agricultor | solo cuenta sus terrenos | 🚧 |
| Race condition transfer terrain entre check e INSERT | TASK-27.09 | TOCTOU teórico | task queda asociada al terrain transferido; saga Kafka limpia al borrarlo | 🚧 |
| i18n `terrain.not.owned` ES y EN | TASK-27.10 | locale | mensajes correctos | 🚧 |

---

## Apéndice A — Matriz de cobertura por capa

| Capa | Casos previstos | Casos hoy verdes | Bloque que los implementa |
|---|---|---|---|
| Unit (Mockito) | ~60 | 28 | §1, §7, §11, §13, §15, §17, §18, §21 |
| WebMvc slice | ~90 | 8 | §1-§6, §7, §8, §9, §10, §12 |
| JDBC (Testcontainers) | ~40 | 0 | §20 entero, casos `+JDBC` |
| gRPC client unit | ~10 | 0 | §14 |
| Kafka listener (unit) | ~8 | 5 | §15-18 |
| Kafka producer (unit) | ~3 | 0 (parcial en §7) | §19 |
| Kafka integración (`@EmbeddedKafka`) | ~12 | 0 | §15.19, §16, §17.11, §18.10, §19.10, §24 |
| Integración cross-service | ~15 | 0 | §24 |
| Contract / WireMock | ~5 | 0 | §14.20-22 |
| Transversales (i18n / ProblemDetail) | ~10 | 0 | §25 |
| Seguridad | ~13 | 0 | §26 |
| Ownership (bloque 🚧) | ~10 | 0 | §27 — depende de implementar |
| Storage / Files | ~8 | 0 | §8, §23 |
| **Total** | **~280** | **~62** | |

> Objetivo de cobertura JaCoCo tras implementar todo: ≥ **70 %** en `service/` y `controller/`, ≥ **60 %** global.

---

## Apéndice B — Fixtures listas para copiar

### B.1 `TaskRequest` válido mínimo

```json
{
  "task_type_code": "IRRIGATION",
  "terrain_id": "11111111-1111-1111-1111-111111111111",
  "planned_at": "2026-06-15T08:00:00",
  "estimated_duration_minutes": 120,
  "assigned_to": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

### B.2 `TaskRequest` completo con recurrencia

```json
{
  "task_type_code": "TREATMENT",
  "terrain_id": "11111111-1111-1111-1111-111111111111",
  "planned_at": "2026-06-15T08:00:00",
  "estimated_duration_minutes": 90,
  "assigned_to": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "planned_inputs": [
    {"input_name": "Cobre 50%", "quantity": 2.5, "unit": "kg"}
  ],
  "notes": "Tratamiento fitosanitario campaña 2026",
  "recurrence": {
    "frequency": "WEEKLY",
    "interval": 1,
    "until": "2026-08-15"
  }
}
```

### B.3 `TaskStateTransitionRequest` (FINISHED)

```json
{
  "to_state": "FINISHED",
  "effective_at": "2026-06-15T10:30:00",
  "real_duration_minutes": 145,
  "consumed_inputs": [
    {"input_name":"Cobre 50%","input_id":"33333333-3333-3333-3333-333333333333","quantity":2.3,"unit":"kg"}
  ],
  "note": "Tratamiento completado sin incidencias"
}
```

### B.4 SQL seeds para tests JDBC

```sql
TRUNCATE TABLE task_evidence, task_state_history, notification, notification_preference, notification_emission_log, task CASCADE;

INSERT INTO task (id, task_type_id, terrain_id, planned_at, estimated_duration_minutes, state, created_by, assigned_to, planned_inputs)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1,
   '11111111-1111-1111-1111-111111111111',
   NOW() + INTERVAL '1 day', 120, 'PENDING',
   'cccccccc-cccc-cccc-cccc-cccccccccccc',
   'dddddddd-dddd-dddd-dddd-dddddddddddd',
   '[]'::jsonb),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 2,
   '11111111-1111-1111-1111-111111111111',
   NOW() - INTERVAL '2 days', 60, 'FINISHED',
   'cccccccc-cccc-cccc-cccc-cccccccccccc',
   'dddddddd-dddd-dddd-dddd-dddddddddddd',
   '[]'::jsonb);
```

### B.5 Mock WireMock para gRPC

Igual que en `season-service/test-suite-plan-season-service.md` Apéndice B.5. Para `CheckTerrainExists` y `ValidateUser` con `wiremock-grpc-extension` o `InProcessServer` de gRPC.

### B.6 Evento Kafka de prueba

```bash
# user-deleted (política D2)
echo '{"userId":"dddddddd-dddd-dddd-dddd-dddddddddddd"}' | \
  kcat -b localhost:9092 -t user-deleted -P \
       -H "__TypeId__=com.agro.authservice.event.UserDeletedEvent"

# stock-low (hub D5 desde input-service)
echo '{"inputId":"33333333-3333-3333-3333-333333333333","inputName":"Cobre 50%","currentStock":2.0,"threshold":5.0,"unit":"kg","createdBy":"dddddddd-dddd-dddd-dddd-dddddddddddd"}' | \
  kcat -b localhost:9092 -t stock-low -P \
       -H "__TypeId__=com.agro.inputservice.event.StockLowEvent"

# sensor-alert (hub D5 desde iot-service)
echo '{"alertId":"55555555-5555-5555-5555-555555555555","sensorId":"66666666-6666-6666-6666-666666666666","terrainId":"11111111-1111-1111-1111-111111111111","variable":"temperature","kind":"above_max","value":42.5,"threshold":35.0,"recordedAt":"2026-06-15T14:00:00Z","notifyUserIds":["dddddddd-dddd-dddd-dddd-dddddddddddd"]}' | \
  kcat -b localhost:9092 -t sensor-alert -P \
       -H "__TypeId__=com.agro.iotservice.event.SensorAlertEvent"
```

### B.7 Helper `@EmbeddedKafka` para producer

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"task-completed", "user-deleted", "terrain-deleted", "stock-low", "sensor-alert"})
class TaskCompletedKafkaIT {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private TaskTransitionService transitionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void e2e_finished_publishes() throws Exception {
        UUID taskId = seedInProgressTreatment();   // helper

        TaskStateTransitionRequest req = new TaskStateTransitionRequest(
            TaskState.FINISHED, null, 145, List.of(/*consumed*/), "ok");
        transitionService.transition(taskId, req, /*userId*/ UUID.randomUUID());

        // consume y verifica
        Consumer<String, Object> consumer = consumerForTopic("task-completed");
        ConsumerRecord<String, Object> record = KafkaTestUtils.getSingleRecord(consumer, "task-completed", Duration.ofSeconds(5));
        assertThat(record.value()).isInstanceOf(TaskCompletedEvent.class);
        assertThat(((TaskCompletedEvent) record.value()).taskId()).isEqualTo(taskId);
    }
}
```

### B.8 Helper file upload multipart

```java
MockMultipartFile file = new MockMultipartFile(
    "file", "evidencia.png", "image/png",
    Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.png")));

mockMvc.perform(multipart("/task/{id}/evidence", taskId).file(file))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.original_name").value("evidencia.png"));
```

---

## Apéndice C — Orden de implementación sugerido

### Fase 1 — completar coverage del estado actual (sin nueva funcionalidad)

1. **§1 entero** — `TaskControllerCreateTest` y `TaskServiceCreateTest`. Un commit.
2. **§2, §3, §4, §5, §6** — controllers slice tests. Un commit por endpoint.
3. **§7 entero** — máquina de transiciones (`TaskTransitionServiceTest` + `TaskControllerTransitionTest`). Un commit.
4. **§8** — evidencias (`TaskEvidenceServiceTest` + `TaskEvidenceControllerTest` + `FileStorageServiceTest`). Un commit.
5. **§9, §10, §11** — dashboard, export, role scoping. Un commit por bloque.
6. **§12, §13** — notificaciones y scheduler. Un commit por bloque.
7. **§14** — gRPC clients (unit). Un commit.
8. **§15-18** — listeners unit. Un commit.
9. **§20 entero** — Testcontainers `*RepositoryIT`. Un commit (requiere Docker).
10. **§21, §22, §23** — utilities. Un commit por clase.
11. **§25** — transversales i18n. Un commit.
12. **§26** — seguridad defensiva. Un commit.

**Hito Fase 1:** todos los tests verdes; cobertura JaCoCo ≥ 60 %.

### Fase 2 — integración

1. **§19** — producer Kafka con `@EmbeddedKafka`. Un commit.
2. **§15.19, §16.10+, §17.11, §18.10** — listeners integración con `@EmbeddedKafka`. Un commit.
3. **§24** — cross-service con WireMock + Testcontainers Kafka. Un commit.
4. **§24.40** — smoke manual documentado en bash script `test-task-plan.sh`.

**Hito Fase 2:** flujos end-to-end probados; cobertura ≥ 70 %.

### Fase 3 — bloque 🚧 ownership

1. Pre-requisito: implementar `CheckTerrainOwnership` en terrain-service + `X-User-Id` en gateway.
2. Refactor: `task-service` controllers reciben `@RequestHeader("X-User-Id") UUID userId`.
3. Implementar §27 entero como TDD-spec.

**Hito Fase 3:** el contrato de propiedad cross-service está cerrado.

### Fase 4 — script QA bash (opcional)

Análogo al `season-service/test-season-plan.sh`:

- Crea user + JWT + terrain (vía gateway).
- POST task.
- Transition PENDING → IN_PROGRESS → FINISHED con consumed_inputs.
- Verifica que `kafkacat -C -t task-completed` recibe el evento.
- Publica `terrain-deleted` con kcat y verifica que la task desaparece.
- Publica `stock-low` y `sensor-alert` y verifica filas en `notification`.

---

## Notas finales

1. **Cualquier cambio en este plan debe ir acompañado de un commit que actualice el `.md` y los tests asociados** — ambos archivos van juntos.
2. **No mezclar tests de fases distintas en el mismo PR**: los 🚧 (§27) deben ir solos para que el revisor pueda razonar sobre el contrato cross-service sin distraerse.
3. **Si un caso del plan es difícil de cubrir** (concurrencia, tiempos, fallo de infra) → marcarlo como `@Tag("flaky")` o `@Disabled` con justificación, **nunca** silenciar la causa raíz.
4. **La fuente de verdad del comportamiento es el código + este plan + `TASK-SERVICE-DOCUMENTATION.md`.** Si los tres divergen, abrir issue.
5. **Algunos tests dependen de paquetes 05 (input) y 06 (iot) — marcados 🚧 hasta que existan** (`CheckInputExists` gRPC, `stock-low` producer real, `sensor-alert` producer real). Mientras tanto, usar mocks/stubs/`@EmbeddedKafka`.
