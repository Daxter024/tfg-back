# iot-service — Documentacion

> Microservicio de sensores IoT del monorepo `tfg-back`. Cubre HU-IOT-01
> (sensores + lecturas) y HU-IOT-02 (umbrales y alertas). Plan de origen:
> `LLM-WORK/06-iot-service-from-main.md` (sin parcels, postgres vainilla,
> sin MQTT en v1).

## 1. Parametros del servicio

| Parametro | Valor |
|---|---|
| Modulo Maven | `iot-service` |
| Paquete Java | `com.agro.iotservice` |
| Spring Boot | 3.5.7 |
| Java | 21 |
| Flyway | 11.7.2 |
| Puerto HTTP | `8086` |
| Puerto gRPC | `9097` (reservado, NO expuesto — iot-service es cliente gRPC, no server) |
| Puerto BBDD | `5438` (`iot_db` postgres:14 vainilla) |
| `consumer.group-id` | `iot-service-group` |
| Topic producido | `sensor-alert` (consumido por task-service como hub D5) |
| Topics consumidos | `terrain-deleted`, `user-deleted` |

## 2. Decisiones aplicadas del plan

| # | Decision | Aplicacion |
|---|---|---|
| D3 | Postgres vainilla + Flyway template para Timescale | `iot-db: postgres:14`. `sensor_reading` es tabla normal + vistas `sensor_reading_hourly` / `sensor_reading_daily`. La migracion `V99__migrate_to_timescaledb.sql.template` vive en `db/migration-disabled/` y NO se ejecuta. |
| D4 | Sin MQTT en v1 | Interfaz `ReadingIngestor` con unica impl `HttpReadingIngestor`. Sin dependencia `spring-integration-mqtt`. Sin contenedor Mosquitto en docker-compose. |
| D5 | task-service es el hub | iot-service produce `sensor-alert` con `notifyUserIds`. NO tiene tabla `notification` propia. |
| — | Sin parcels | `sensor.terrain_id NOT NULL`. gRPC `CheckTerrainExists`. Consume `terrain-deleted`. `SensorAlertEvent.terrainId`. |

## 3. Esquema de base de datos

| Migracion | Crea |
|---|---|
| `V1__create_sensor_and_reading.sql` | `variable_kind` enum, `sensor`, `sensor_reading` con PK (sensor_id, recorded_at) para ingest idempotente, vistas `sensor_reading_hourly` / `sensor_reading_daily`, `device_api_key` con hash BCrypt |
| `V2__create_threshold_and_alert.sql` | `threshold` con tres CHECK constraints (XOR scope, bounds meaningful, bounds ordered), enums `alert_state` y `alert_kind`, `sensor_alert` con indice parcial para queries abiertas |

**Decisiones de esquema:**

- `sensor_reading.PRIMARY KEY (sensor_id, recorded_at)` permite reintentar
  ingest con `ON CONFLICT DO NOTHING` sin duplicar filas (idempotencia §8.2).
- `device_api_key.key_hash` con BCrypt cost 12. La clave plana se devuelve
  UNA VEZ y nunca se persiste. Rotacion deactiva la activa anterior
  atomicamente.
- `threshold` constraint XOR: o `sensor_id` (regla por sensor) o `variable`
  (regla global por tipo), nunca ambos. Por sensor prevalece a la hora de
  evaluar (logica en `AlertService`, no en SQL).
- `sensor_alert` indice `idx_alert_sensor_open` parcial `WHERE state <>
  'resolved'` para que la query de dedup sea O(1).

## 4. Endpoints REST

Todos los `/sensor /threshold /alert` van via gateway `/api/...` con
`StripPrefix=1 + JwtValidation`. La excepcion es `/api/ingest/**` que NO
lleva JwtValidation y se autentica via header `X-Device-Key` dentro del
servicio (ver §6).

### Sensors

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `GET` | `/sensor` | `terrain_id?`, `variable?`, `status?` | `List<Sensor>` |
| `GET` | `/sensor/{id}` | — | `Sensor` con `last_value` rellenado |
| `POST` | `/sensor` | `SensorRequest` + header `X-User-Id` | `201 {id, message}` |
| `PATCH` | `/sensor/{id}` | `SensorUpdateRequest` | `200 {message}` |
| `DELETE` | `/sensor/{id}` | — | `204` (cascade sensor_reading + sensor_alert + device_api_key) |
| `POST` | `/sensor/{id}/api-key` | — | `201 {id, key, message}` — `key` es la secret en plano UNA VEZ |
| `GET` | `/sensor/{id}/reading` | `from`, `to`, `agg=raw/hourly/daily?` | `{agg, data: [...]}` |

### Thresholds

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `GET` | `/threshold` | `sensor_id?`, `variable?` | `List<Threshold>` |
| `GET` | `/threshold/{id}` | — | `Threshold` |
| `POST` | `/threshold` | `ThresholdRequest` | `201 {id, message}` |
| `PATCH` | `/threshold/{id}` | `ThresholdUpdateRequest` (con `clear_min` / `clear_max`) | `200` |
| `DELETE` | `/threshold/{id}` | — | `204` |

### Alerts

| Metodo | Ruta | Body / Query | Respuesta |
|---|---|---|---|
| `GET` | `/alert` | `state?`, `terrain_id?`, `from?`, `to?` | `List<SensorAlert>` |
| `GET` | `/alert/{id}` | — | `SensorAlert` |
| `POST` | `/alert/{id}/review` | `AlertReviewRequest` + header `X-User-Id` | `200 {message}` |
| `POST` | `/alert/{id}/resolve` | — | `200 {message}` |

### Device ingest (sin JWT)

| Metodo | Ruta | Headers | Respuesta |
|---|---|---|---|
| `POST` | `/ingest/sensor/{id}/reading` | `X-Device-Key: <plain>` (BCrypt validado), `Content-Type: application/json` | `201 {inserted: N}` |

`POST` sin header o con header invalido devuelve `401` con respuesta vacia
(no diferenciamos "sensor no existe" de "key mal" para no filtrar info via
enumeracion).

## 5. Default `agg` segun rango de tiempo

| Rango (`to - from`) | `agg` por defecto | Header |
|---|---|---|
| `<= 1 h` | `raw` | — |
| `<= 24 h` | `hourly` | — |
| `<= 7 d` | `daily` | — |
| `<= 31 d` | `daily` | — |
| `> 31 d` | `daily` | `X-Downsampled: true` |
| `> 365 d` | — (400 `reading.range.too-wide`) | — |

## 6. DeviceKeyAuthFilter

`OncePerRequestFilter` aplicado SOLO a `/ingest/**` (shouldNotFilter
devuelve `true` para cualquier otra ruta). Extrae sensorId del path,
busca el header `X-Device-Key`, valida contra cualquier hash activo en
`device_api_key` via `BCrypt.checkpw`. Cualquier fallo (header ausente,
sensor inexistente, UUID malformado, key incorrecta) devuelve `401` con
body vacio.

## 7. Threshold evaluation + alert dedup

`AlertService` implementa `SensorReadingService.AlertEvaluator` y se
auto-registra via `@PostConstruct` para que cada lectura insertada pase
por el evaluador dentro de la misma transaccion. Logica (plan §7.2):

1. Resolver threshold: por `sensor_id` gana sobre por `variable`.
2. Clasificar: `below_min` / `above_max` / `in_range`.
3. Si out-of-range y ya hay alerta abierta del mismo `kind` ->
   `UPDATE reading_count = reading_count + 1, last_recorded_at = ?`.
   NO se publica Kafka (anti-spam).
4. Si out-of-range y no hay alerta abierta -> `INSERT` + `publishSensorAlert`.
5. Si in-range y hay alertas abiertas -> `auto-resolve` todas.

Esto da una sola fila + un solo evento `sensor-alert` por incidente,
sin importar cuantas lecturas caigan fuera de rango durante la rafaga.

## 8. Listeners Kafka

| Topic consumido | Listener | Accion |
|---|---|---|
| `terrain-deleted` | `TerrainDeletedListener` | `DELETE FROM sensor WHERE terrain_id = ?` (cascade) |
| `user-deleted` | `UserDeletedListener` | `DELETE FROM sensor WHERE created_by = ?` (cascade, politica RGPD) |

Type-mapping en `application.properties`:

```
spring.kafka.consumer.properties.spring.json.type.mapping=\
  com.agro.terrainservice.event.TerrainDeletedEvent:com.agro.iotservice.event.TerrainDeletedEvent,\
  com.agro.authservice.event.UserDeletedEvent:com.agro.iotservice.event.UserDeletedEvent
```

## 9. gRPC clients

- `TerrainGrpcClient -> terrain-service:9093 CheckTerrainExists` —
  validacion sincrona al crear sensor.
- `UserGrpcClient -> auth-service:9091 ValidateUser` — pre-flight de cada
  UUID en `threshold.notify_user_ids`.

No exponemos servicio gRPC: ningun otro modulo del monorepo necesita
`CheckSensorExists` (decision MICROSERVICES-RELATIONSHIPS §7.3).

## 10. Smoke E2E

```bash
# arranque
cd iot-service && docker compose up -d --build && sleep 10

# 1) Sin sensores
curl -s -H "X-User-Id: $UID_ADMIN" -H "X-User-Role: administrador" \
  http://localhost:8086/sensor
# -> []

# 2) Ingest sin X-Device-Key -> 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  -H "Content-Type: application/json" \
  -d '{"readings":[{"recorded_at":"2026-05-11T10:00:00Z","value":25}]}' \
  http://localhost:8086/ingest/sensor/00000000-0000-0000-0000-000000000000/reading
# -> 401
```

## 11. Tests

| Clase | Cobertura |
|---|---|
| `IotServiceApplicationTests` | smoke Testcontainers postgres:14 (skip si Docker no disponible) |
| `SensorServiceTest` | create con terrain valido / invalido, defaults, last_value lazy |
| `SensorStatusSchedulerTest` | delega a `markNoSignalIfStale` |
| `SensorServiceTest` + `ReadingControllerTest` | agg defaults segun rango + 400 sobre rango > 365 d |
| `DeviceKeyServiceTest` | round-trip BCrypt del secret generado, 404 sobre sensor faltante |
| `DeviceKeyAuthFilterTest` | bypass /sensor, filtro /ingest, missing/wrong/valid key, UUID malformado |
| `IngestControllerTest` | 201 happy, 400 empty batch, 401 missing key (filter end-to-end) |
| `ThresholdServiceTest` | XOR, bounds_meaningful, bounds_ordered, notify_user_ids gRPC, happy |
| `AlertServiceTest` | in_range auto-resolve, primera fuera -> INSERT + Kafka, siguientes -> count++ sin Kafka, below_min payload, sin threshold, sensor inexistente |
| `ThresholdControllerTest` | list, create happy, 400 sobre XOR |
| `AlertControllerTest` | list, get 200/404, review delega reviewer al X-User-Id, resolve 200/404 |
| `TerrainDeletedListenerTest` / `UserDeletedListenerTest` | delegan a SensorService |

`./mvnw -pl iot-service test` -> 44/44 verdes en este HEAD.

## 12. Anti-patrones evitados

- Sin FK cross-servicio (`terrain_id` es UUID suelto verificado por gRPC).
- Sin Kafka antes del commit: `EventPublisher` se llama desde el mismo
  `@Transactional` que el INSERT de la alerta, pero el `KafkaTemplate.send`
  ocurre solo a la salida del scope. Si el INSERT falla, no se publica.
- Sin secretos hardcodeados: las API keys de dispositivo son aleatorias y
  almacenadas con BCrypt.
- Sin parcels: confirmado, `grep -rln -i parcel iot-service/src/` debe
  devolver vacio.
