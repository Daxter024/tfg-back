# Ownership cross-service — notas de implementación

> **Rama:** `feat-task-service` (commits encadenados al final de la rama).
> **Fecha:** 2026-05-11.
> **Trigger:** revisión manual descubrió que `task-service` validaba **existencia** del `terrain_id` pero NO **propiedad** ni inyectaba `X-User-Id` correctamente desde el gateway.

## Estado previo (antes de esta tanda de commits)

| Componente | Estado |
|---|---|
| `auth-service.JwtUtil` | ✅ ya emite JWT con claim `userId` (línea 37). No cambia. |
| `api-gateway.JwtValidationGatewayFilterFactory` | ❌ valida firma del JWT vía `/validate` pero **NO propaga** `X-User-Id` al downstream. |
| `task-service.TaskController` | 🟡 ya declara `@RequestHeader("X-User-Id") UUID userId` — listo para recibir, pero recibía `null` o fallaba `400 missing required header` cuando se llamaba vía gateway. |
| `terrain-service.TerrainGrpcService` | 🟡 solo `CheckTerrainExists(terrain_id)`. Sin RPC de ownership. |
| `task-service.TaskService.createTask` | ❌ solo `checkTerrainExists`. Cualquier user podía crear task sobre cualquier terreno. |

## Diseño aplicado

### 1. api-gateway — decodifica JWT y propaga claims

`JwtValidationGatewayFilterFactory.apply()`:

1. Pide `/validate` a auth-service (verifica firma + revocación). Si falla → 401.
2. Si OK, decodifica **localmente** el payload (Base64 URL del segmento intermedio) — no re-verifica la firma porque auth-service ya lo hizo.
3. Extrae claims `userId` y `role`.
4. **Strip** cualquier `X-User-Id` o `X-User-Role` entrante del cliente (anti-spoof).
5. Muta el request con `X-User-Id: <uuid>` y `X-User-Role: <role>`.
6. Continúa el chain.

No requiere `jjwt` en el gateway — el payload se decodifica con `java.util.Base64` + `Jackson` (ya disponible vía Spring). La firma ya quedó validada en el paso 1.

### 2. terrain-service — nuevo RPC `CheckTerrainOwnership`

`terrain.proto` añade:

```proto
service TerrainService {
  rpc CheckTerrainExists    (TerrainIdRequest)        returns (TerrainExistsResponse);
  rpc CheckTerrainOwnership (TerrainOwnershipRequest) returns (TerrainOwnershipResponse);  // NUEVO
}
message TerrainOwnershipRequest  { string terrain_id = 1; string user_id = 2; }
message TerrainOwnershipResponse { bool exists = 1; bool owned_by_user = 2; }
```

Implementación: `SELECT user_id FROM terrain WHERE id = ?` → comparar con el `user_id` del request.

- UUID malformado en cualquiera de los inputs → `exists=false, owned=false` (no error).
- Terrain no existe → `exists=false, owned=false`.
- Terrain existe pero `user_id` no coincide → `exists=true, owned=false`.
- Coincide → `exists=true, owned=true`.

El RPC viejo `CheckTerrainExists` **se conserva** para retrocompatibilidad (lo usan `season-service` y los listeners actuales).

### 3. task-service — usa el nuevo RPC

**Reemplazo en `TaskService.createTask`:**

```java
// ANTES:
if (!terrainGrpcClient.checkTerrainExists(request.terrain_id())) {
    throw new TerrainNotFoundException(...);
}

// DESPUÉS:
TerrainOwnership o = terrainGrpcClient.checkTerrainOwnership(request.terrain_id(), createdBy);
if (!o.exists()) throw new TerrainNotFoundException(...);
if (!o.ownedByUser()) throw new ForbiddenException(i18n.get("terrain.not.owned"));
```

**Nueva excepción + handler:**

- `ForbiddenException extends RuntimeException`
- `GlobalExceptionHandler.handleForbidden` → `HttpStatus.FORBIDDEN (403)` con `ProblemDetail` y `i18n: terrain.not.owned`.

**i18n nuevas keys:**

```properties
# es
terrain.not.owned=Este terreno no pertenece al usuario actual

# en
terrain.not.owned=This terrain does not belong to the current user
```

### Alcance NO incluido en este lote (siguiente PR)

- `task-service.updateTask` (PATCH) — TaskController hoy NO implementa update; cuando se haga (issue TASK-5.NN), añadir la misma comprobación.
- `GET /task/{id}`, `DELETE /task/{id}`, `POST /task/{id}/transition` — hoy validan **rol** vía `RoleScopingService` pero no comprueban ownership directo sobre la task individual. Cuando un agricultor pida una task de otro user terminará en 404 (al filtrarse por sus `terrain_id`), lo que es aceptable. Caso `tecnico` mirando task ajena → 404 también (filtra por `assigned_to`). Caso explotable: un agricultor que sabe el UUID de una task de otro agricultor → 404 OK. Solo `admin` ve todas. **No hay regresión funcional** aquí.
- `season-service` y futuros `input-service`/`iot-service` — mismo gap, fuera del scope de la rama actual.
- `task-service.TerrainHttpClient.findTerrainIdsByUser` — la query REST cross-service para el scope `agricultor`. Se podría reemplazar por un RPC `ListTerrainsByUser`, pero supone tocar el contrato. Fuera del scope.

## Orden de commits

1. `feat(api-gateway): propagate X-User-Id and X-User-Role from JWT claims`
2. `feat(terrain-service): add CheckTerrainOwnership gRPC RPC`
3. `feat(task-service): require terrain ownership on createTask + ForbiddenException`
4. `test(task-service): ownership tests (TASK-27.02..04)`
5. `docs(task-service): activate §27 in plan + bash script + this notes file`

## Verificación

- `./mvnw -pl task-service -am test` → todos verdes.
- `./mvnw -pl terrain-service test` → todos verdes (el RPC nuevo no rompe el existente).
- `./mvnw -pl api-gateway compile` → compila.
- Smoke E2E: cliente con JWT de user A intenta `POST /task` con `terrain_id` de B → 403. Mismo cliente sobre `terrain_id` suyo → 201.

## Trazabilidad

- `LLM-WORK/04-task-service-from-main.md` §11.5, §15.10-12, §16, §17, §18 — referencias a `X-User-Id` y políticas que ya asumían propagación.
- `MICROSERVICES-RELATIONSHIPS.md` §7.4, §8.1 — diseño general de ownership.
- `task-service/test-suite-plan-task-service.md` §27 — los 10 casos del bloque 🚧.
