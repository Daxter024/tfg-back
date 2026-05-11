# input-service — Documentacion

> Microservicio de insumos y stock del monorepo `tfg-back`. Cubre
> HU-INS-01 (control de stock) y HU-INS-02 (trazabilidad por tarea).
> Plan de origen: `LLM-WORK/05-input-service-from-main.md`.

## 1. Parametros del servicio

| Parametro | Valor |
|---|---|
| Modulo Maven | `input-service` |
| Paquete Java | `com.agro.inputservice` |
| Spring Boot | 3.5.7 |
| Java | 21 |
| Flyway | 11.7.2 |
| Puerto HTTP | `8085` |
| Puerto gRPC | `9096` (expuesto — server CheckInputExists) |
| Puerto BBDD | `5437` (`input_db` postgres:14) |
| `consumer.group-id` | `input-service-group` |
| Topic producido | `stock-low` |
| Topics consumidos | `task-completed`, `user-deleted` |

## 2. Esquema de base de datos

| Migracion | Crea |
|---|---|
| `V1__create_input_and_movement.sql` | `input` (con soft-delete + indice trigram), `input_movement` (ON DELETE RESTRICT), vista `input_with_stock`, tabla `stock_alert_log`, enums `input_category` y `movement_kind` |

**Decisiones de esquema:**

- `input.deleted_at IS NOT NULL` excluye la fila de listados pero la
  conserva por trazabilidad historica.
- NO existe columna `stock` — siempre se calcula con la vista
  `input_with_stock` o un sub-SELECT equivalente.
- `input_movement.input_id` con `ON DELETE RESTRICT`: bloquea cualquier
  intento de tirar un input que tenga movimientos. Para "borrar" un
  input se usa soft-delete.
- `performed_by` es nullable para soportar anonymizacion tras `user-deleted`.

## 3. Endpoints REST

Todos bajo `/api/input/**` cuando se accede via gateway (`StripPrefix=1`).

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `GET` | `/input` | `category?`, `q?` (trigram), `low_stock_only?`, `include_deleted?`, `page`, `size` | `PageResponse<Input>` |
| `GET` | `/input/{id}` | — | `Input` (incluye `current_stock`) |
| `POST` | `/input` | `InputRequest` | `201` + `{id, message}` |
| `PATCH` | `/input/{id}` | `InputUpdateRequest` | `200` |
| `DELETE` | `/input/{id}` | — | `204` (soft-delete idempotente) |
| `GET` | `/input/{id}/movement` | `kind?`, `task_id_not_null?`, `from?`, `to?`, `page`, `size` | `PageResponse<InputMovement>` |
| `POST` | `/input/{id}/movement` | `MovementRequest` | `201` + `{id, message}` |
| `POST` | `/input/movement/{id}/revert` | — | `201` (crea IN de igual cantidad, `reason=TASK_REVERT`) |

**Restricciones:**
- `reason=TASK` o `TASK_REVERT` rechazadas en `POST /movement`
  (reservadas al flujo interno).
- Cambiar `category` con PATCH solo si el input no tiene movimientos.
- `quantity > 0` (DTO + check DB).
- `occurred_at <= today` (DTO `@PastOrPresent`).

## 4. Autenticacion / identidad

El gateway valida el JWT y propaga `X-User-Id`. input-service lo lee como
`@RequestHeader(name = "X-User-Id") UUID userId` en `POST /input` y `POST
/input/{id}/movement` (para `created_by` y `performed_by`).
Sin la cabecera Spring devuelve 400 automaticamente.

## 5. gRPC

input-service es **server** del servicio
`com.agro.input.grpc.InputService` en el puerto 9096.

```proto
service InputService {
  rpc CheckInputExists (InputIdRequest) returns (InputExistsResponse);
}
```

UUID invalido → `exists=false` (no lanza error gRPC). UUID valido con
input soft-deleted → tambien `false` (task-service no debe permitir
enlazar inputs muertos a planificacion futura).

## 6. Kafka

### 6.1 Producido

- `stock-low` (key = `inputId`): payload
  `StockLowEvent(inputId, inputName, currentStock, threshold, unit, createdBy)`.
  `createdBy` viene pre-calculado para que el hub (task-service) no tenga
  que resolverlo via auth-service. Anti-spam 24h gestionado dentro del
  service `StockAlertService` con la tabla `stock_alert_log`.

### 6.2 Consumido

- `task-completed` (task-service): por cada `ConsumedInput` con
  `input_id` no nulo crea un OUT con `reason=TASK`. Si `input_id` es
  null (entrada libre) se ignora. Excepciones por input concreto se
  loggean y se continua con el resto del lote.
- `user-deleted` (auth-service): anonymiza movimientos
  (`performed_by=NULL`) y soft-deletes inputs `created_by=userId`. NO se
  borran los movimientos — se conservan por trazabilidad normativa.

### 6.3 Type-mapping

```properties
spring.kafka.consumer.properties.spring.json.type.mapping=\
  com.agro.taskservice.event.TaskCompletedEvent:com.agro.inputservice.event.TaskCompletedEvent,\
  com.agro.authservice.event.UserDeletedEvent:com.agro.inputservice.event.UserDeletedEvent
```

## 7. Stock-low — logica de emision

Implementada en `StockAlertService.checkAndEmitLowStock(inputId)`.
Se llama tras CADA movimiento (manual via REST, automatico via
`TaskCompletedListener`, o reversion `TASK_REVERT`).

| Estado previo | Stock vs threshold | Accion |
|---|---|---|
| Sin `low_stock_threshold` | n/a | nunca emite |
| Sin log o `is_currently_below=false` | nuevo: below | emite + log `(below=true)` |
| `is_currently_below=true`, ≤24h | sigue below | no emite |
| `is_currently_below=true`, >24h | sigue below | re-emite + log `(below=true)` |
| `is_currently_below=true` | cruza above | log `(below=false)` (no emite) |
| `is_currently_below=false` | vuelve below | emite + log `(below=true)` inmediatamente |

Excepcion al publicar en Kafka: log warn + se marca igual el log como
below (evita spam tras recuperarse el broker). El movimiento ya esta
persistido — el error no debe propagar.

## 8. Decisiones de diseno

| # | Decision | Razon |
|---|---|---|
| D5 | Notificaciones centralizadas en task-service | input-service SOLO publica `stock-low`; el hub crea la notif. NO hay REST inverso ni tabla `notification` local. |
| — | Sin parcels | `task-completed` consumido aqui llega con `terrainId` pero NO con `parcelId`. Alineado con el resto del monorepo. |
| — | Catalogo "historico" | Borrar un input NO publica `input-deleted`. Los movimientos conservan referencia para registro normativo (mismo criterio que en MICROSERVICES-RELATIONSHIPS.md §7.1). |
| — | gRPC `CheckInputExists` filtra soft-deleted | task-service no debe permitir referenciar un input muerto en planificaciones futuras. |
| — | `revert` solo de movimientos OUT | revertir un IN no tiene sentido (era una compra/donacion real); para "cancelar" un IN se usa un OUT manual con `reason=OTHER`. |

## 9. Tests

41 tests verdes en `./mvnw -pl input-service test`. Distribucion:

| Clase | Tests | Cobertura |
|---|---|---|
| `InputServiceTest` | 8 | CRUD, duplicados, category immutable, soft-delete idempotente |
| `MovementServiceTest` | 7 | insert + alert hook, rechazo TASK por REST, revert IN/OUT |
| `StockAlertServiceTest` | 8 | 5 transiciones de estado del log + fallo de publisher |
| `TaskCompletedListenerTest` | 5 | input_id null, lista null, multiples inputs, fallo aislado |
| `UserDeletedListenerTest` | 2 | con datos / sin datos |
| `InputGrpcServiceTest` | 3 | UUID valido true/false, UUID invalido false |
| `InputControllerTest` | 8 | endpoints 200/201/204/400/404 |
| `InputServiceApplicationTests` | 1 (assume-skip sin Docker) | contextLoads + Flyway en Testcontainers |

## 10. Coordinacion cross-paquete

- task-service ya tiene `StockLowListener` registrado con type-mapping
  para `com.agro.inputservice.event.StockLowEvent`. En cuanto este
  servicio empiece a publicar las notifs aparecen solas en task.
- task-service ya publica `task-completed` con payload sin `parcelId`.
  El listener aqui deserializa con `terrainId`.
- gRPC `CheckInputExists` se puede usar opcionalmente por task-service
  para validar `planned_inputs[].input_id` (configuracion bajo
  `@ConditionalOnProperty` en el cliente).
