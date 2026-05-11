# task-service — Documentacion

> Microservicio de tareas agrarias del monorepo `tfg-back`. Cubre HU-TAR-01..04
> + decision D2 (politica user-deleted) + decision D5 (hub central de
> notificaciones). Plan de origen: `LLM-WORK/04-task-service-from-main.md`.

## 1. Parametros del servicio

| Parametro | Valor |
|---|---|
| Modulo Maven | `task-service` |
| Paquete Java | `com.agro.taskservice` |
| Spring Boot | 3.5.7 |
| Java | 21 |
| Flyway | 11.7.2 |
| Puerto HTTP | `8084` |
| Puerto gRPC | `9095` (reservado, **no expuesto**) |
| Puerto BBDD | `5436` (`task_db` postgres:14) |
| `consumer.group-id` | `task-service-group` |
| Topic producido | `task-completed` |
| Topics consumidos | `user-deleted`, `terrain-deleted`, `stock-low`, `sensor-alert` |

## 2. Esquema de base de datos

| Migracion | Crea |
|---|---|
| `V1__create_task_table.sql` | `task_type` (catalogo con seed), `task`, `task_state_history` |
| `V2__create_task_evidence.sql` | `task_evidence` (ON DELETE CASCADE → task) |
| `V3__create_task_notifications.sql` | `notification_preference`, `notification` (task_id nullable), `notification_emission_log` (anti-spam) |

**Decisiones:** la tabla `task` no tiene `parcel_id` ni `season_id` (D1+sin-parcels). La unica referencia geografica es `terrain_id UUID NOT NULL`. La relacion tarea↔temporada se resuelve en frontend uniendo por `terrain_id + planned_at`.

## 3. Endpoints REST

Todos bajo `/api/task/**` o `/api/notification/**` cuando se acceden via gateway (que aplica `StripPrefix=1`).

### 3.1 Tareas

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `POST` | `/task` | `TaskRequest` | `201` + `{id, message}` |
| `PATCH` | `/task/{id}` | `TaskUpdateRequest` | `200` |
| `DELETE` | `/task/{id}` | — | `204` o `409` (history) |
| `GET` | `/task` | filtros + `fields?` + paginacion | `PageResponse<TaskSummaryDTO>` |
| `GET` | `/task/{id}` | `fields?` | `Map<String,Object>` |
| `GET` | `/task/calendar` | `from`, `to`, `view=week/month/year` | `List<TaskCalendarSlotDTO>` |
| `POST` | `/task/{id}/transition` | `TaskStateTransitionRequest` | `200` |
| `GET` | `/task/dashboard` | filtros | totals + by_week + by_type |
| `GET` | `/task/export.csv` | filtros | text/csv streaming |

### 3.2 Evidencias

| Metodo | Ruta | Body | Respuesta |
|---|---|---|---|
| `POST` | `/task/{id}/evidence` | multipart `file` (jpeg/png/pdf, ≤10MB) | `201` + `{id}` |
| `GET` | `/task/{id}/evidence` | — | `List<TaskEvidence>` |
| `GET` | `/task/{id}/evidence/{evidenceId}/content` | — | binary stream |
| `DELETE` | `/task/{id}/evidence/{evidenceId}` | — | `204` |

### 3.3 Notificaciones

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `GET` | `/notification` | `page`, `size` | `List<Notification>` |
| `GET` | `/notification/unread-count` | — | `{count}` |
| `POST` | `/notification/{id}/read` | — | `{updated}` |
| `POST` | `/notification/mark-all-read` | — | `{updated}` |
| `GET` | `/notification/preferences` | — | `NotificationPreference` (default si no existe) |
| `PUT` | `/notification/preferences` | `NotificationPreferenceDTO` | `204` |

## 4. Autenticacion / identidad

El gateway valida el JWT (filtro `JwtValidation`) y propaga al backend las cabeceras `X-User-Id` (UUID) y, donde corresponde, `X-User-Role`. task-service NO decodifica el token — solo lee esas cabeceras. Si una request llega sin `X-User-Id`, Spring devuelve `400` automaticamente porque la cabecera es `required = true`.

## 5. gRPC

task-service es **solo cliente** (puerto 9095 reservado pero no expuesto).

- `UserGrpcClient` → `auth-service:9091.ValidateUser(userId)`.
- `TerrainGrpcClient` → `terrain-service:9093.CheckTerrainExists(terrainId)`.

Ambos se invocan dentro de `TaskService.createTask` antes de cualquier INSERT.

## 6. Kafka

### 6.1 Producido

- `task-completed`: payload `TaskCompletedEvent(taskId, taskTypeCode, terrainId, performedBy, finishedAt, consumedInputs[])`. Se emite al pasar una tarea a `FINISHED` (dentro del mismo `@Transactional` que aplica la transicion).

### 6.2 Consumido

- `user-deleted` (auth-service): politica **D2** — borrar `PENDING/IN_PROGRESS/CANCELLED` + anonymizar `FINISHED` con UUID-cero. Borra tambien notifs y prefs del user.
- `terrain-deleted` (terrain-service): borra tasks del terrain. `task_state_history` y `task_evidence` caen por `ON DELETE CASCADE`.
- `stock-low` (input-service): crea `notification(source_kind=STOCK_LOW)` para `createdBy`, con anti-spam 24h via `notification_emission_log`.
- `sensor-alert` (iot-service): crea N notifs (una por destinatario en `notifyUserIds`); agrupa en una sola si > 5 alertas/h del mismo sensor para el mismo user.

### 6.3 Type-mapping

Configurado en `application.properties`:

```
com.agro.authservice.event.UserDeletedEvent      -> com.agro.taskservice.event.UserDeletedEvent
com.agro.terrainservice.event.TerrainDeletedEvent -> com.agro.taskservice.event.TerrainDeletedEvent
com.agro.inputservice.event.StockLowEvent        -> com.agro.taskservice.event.StockLowEvent
com.agro.iotservice.event.SensorAlertEvent       -> com.agro.taskservice.event.SensorAlertEvent
```

## 7. Scheduler de notificaciones

`NotificationSchedulerService.tick()` corre cada 60s (`task.scheduler.delay-ms`) y:

1. **UPCOMING** — busca `PENDING` cuya `planned_at - lead` cae en `now`; resuelve lead via `notification_preference.task_type_lead_minutes->>code` o `default_lead_minutes`; dedup por `(task, kind, user)`.
2. **OVERDUE** — busca `PENDING/IN_PROGRESS` cuya `planned_at + duration < now`; 1 por dia por user; si pasa de 10 en el dia, colapsa a `TASK_DIGEST`.
3. Respeta `quiet_hours_start..quiet_hours_end` (soporta franjas que cruzan medianoche) para EMAIL; IN_APP nunca se silencia.
4. Si `also_notify_creator=true` y el creador != assignee, genera notif adicional.

Desactivable en tests con `task.scheduler.enabled=false`.

## 8. Politica de borrado (saga D2)

Recibido `user-deleted(userId)`:

1. `DELETE FROM task WHERE (created_by=? OR assigned_to=?) AND state IN ('PENDING','IN_PROGRESS','CANCELLED');`
2. `UPDATE task SET assigned_to = '00000000-0000-0000-0000-000000000000' WHERE assigned_to=? AND state='FINISHED';`
3. `UPDATE task SET created_by  = '00000000-0000-0000-0000-000000000000' WHERE created_by=? AND state='FINISHED';`
4. `DELETE FROM notification WHERE user_id=?; DELETE FROM notification_preference WHERE user_id=?;`

Si task-service intenta crear una task con `created_by = UUID-cero` (ej. reactivacion automatica de actividad heredada), `TaskService.validateUserExists` reconoce el placeholder y omite la llamada gRPC.

## 9. Variables de entorno relevantes (perfil `prod`)

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8084` | Puerto HTTP. |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | — | Conexion PostgreSQL. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Broker. |
| `ATTACHMENTS_STORAGE_ROOT` | `/data/attachments` | Raiz del storage local. |
| `TERRAIN_SERVICE_URL` | `http://terrain-service:8080` | Para la consulta REST del role scoping. |
| `SMTP_HOST/PORT/USER/PASS` | — | Opcional; si falta, MailService degrada a log + in-app. |

## 10. Test plan

61 tests verdes:

| Clase | Tests | Cubre |
|---|---|---|
| `TaskServiceTest` | 12 | creacion, validaciones, expansion recurrencia, delete policy, calendar range. |
| `TaskTransitionServiceTest` | 8 | maquina transiciones, validaciones FINISHED, publicacion Kafka, exigencias por tipo. |
| `TaskExportServiceTest` | 2 | export 10k filas streaming, escape RFC 4180. |
| `RoleScopingServiceTest` | 4 | admin / agricultor / tecnico / fallback. |
| `NotificationServiceTest` | 11 | dedup stock-low 24h, sensor-alert grouping, digest, quiet_hours cruzando medianoche, resolveLead, upsert, deleteByUserId, default prefs. |
| `NotificationSchedulerServiceTest` | 5 | dentro/fuera lead window, also_notify_creator, quiet_hours skip email, overdue emit. |
| `TaskServiceUserDeletedTest` | 1 | politica D2 (3 SQL invocados con args correctos). |
| `UserDeletedListenerTest` | 1 | delega a TaskService + NotificationService. |
| `TerrainDeletedListenerTest` | 1 | delega a TaskService.deleteByTerrainId. |
| `StockLowListenerTest` | 1 | delega a NotificationService.createFromStockLow. |
| `SensorAlertListenerTest` | 2 | una notif por destinatario; null skip. |
| `TaskControllerTest` | 8 | POST 201, fecha pasada 400, terrain 404, GET id, GET list, DELETE 409, DELETE 404, /calendar view invalida. |
| `RecurrenceExpanderTest` | 5 | DAILY/WEEKLY/MONTHLY, until cero, exceeded, null spec. |
| `TaskServiceApplicationTests` | 0/1 (skipped) | smoke con Testcontainers Postgres 14; auto-skip si Docker no disponible. |

Ejecutar:

```bash
./mvnw -pl task-service -am test
```

## 11. Limitaciones conocidas

1. **Sin gRPC server**: si en el futuro otro servicio necesita validar `task_id`, hay que anadir `task.proto` + `TaskGrpcService` y publicar el puerto 9095.
2. **gRPC validation de `planned_inputs[].input_id`**: no se valida via `input-service.CheckInputExists` mientras `grpc.client.input-service.address` no este definido. El paquete 05 introducira el `InputGrpcClient` con `@ConditionalOnProperty`.
3. **Anti-spam de SENSOR_ALERT** cuenta emisiones, no entries — un user con muchos sensores distintos puede recibir notifs de varios sensores en la misma hora, lo cual es correcto (anti-spam es por sensor, no global).
4. **`TaskServiceApplicationTests.contextLoads`** se auto-omite cuando Docker no esta disponible; en CI con Docker valida que las 3 migraciones aplican sobre un postgres:14 fresco.
