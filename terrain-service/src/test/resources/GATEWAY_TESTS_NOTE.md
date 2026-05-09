# Gateway tests pendientes (sección 13 del plan)

Los siguientes casos del plan **NO** se implementan en `terrain-service` porque
viven en el módulo `api-gateway`. Se listan aquí para que puedan migrarse cuando
se aborde el paquete de tests del gateway.

| ID       | Caso                                                  | Resultado esperado |
| -------- | ----------------------------------------------------- | ------------------ |
| TER-13.01 | `GET /api/terrain` sin Authorization                 | `401`              |
| TER-13.02 | `GET /api/terrain` con JWT inválido                  | `401`              |
| TER-13.03 | `GET /api/terrain` con JWT expirado                  | `401`              |
| TER-13.04 | `GET /api/terrain` con JWT válido                    | `200` (lo procesa terrain-service) |
| TER-13.05 | `POST /api/terrain/import` con JWT                   | la petición llega al microservicio |
| TER-13.06 | Circuit breaker abre tras 5xx (terrain-service caído) | `503` `/fallback/terrain` |

**Capa recomendada:** integración con `@SpringBootTest` levantando
`auth-service` + `terrain-service` + `api-gateway` (o stubs HTTP), o tests
WebTestClient contra `api-gateway` con `terrain-service` mockeado.

Otros casos del plan que requieren infraestructura no disponible en el entorno
local de tests:

- **TER-7.01–7.03** (descarga JPG/PNG/PDF reales): cubierto a nivel unit en
  `AttachmentServiceTest` (resource construction) y a nivel WebMvc en
  `AttachmentControllerTest` con `Content-Type` / `Content-Length` /
  `Content-Disposition`. La integración real con disco se cubre en
  `LocalFileStorageServiceTest`. La integración end-to-end queda pendiente.
- **TER-9.19** (timeout WireMock): el `RestClient` por defecto no expone un
  timeout configurable corto; requiere ajustar el bean. Se cubre indirectamente
  el camino de `ResourceAccessException` con TER-9.20 (conexión rechazada).
- **TER-12.10–12.20 con Testcontainers PostGIS**: ver
  `TerrainPostgisIntegrationTest`, marcado con
  `@Testcontainers(disabledWithoutDocker = true)` para no romper el build cuando
  el daemon Docker no es accesible (p.ej. version-mismatch del cliente).
- **TER-11.x con EmbeddedKafka**: cubiertos por
  `UserDeletedListenerKafkaTest` los casos TER-11.02 y TER-11.06. Los casos
  TER-11.05 (payload null), TER-11.07 (mapping cross-paquete) y TER-11.08
  (payload corrupto) requieren producir mensajes con headers personalizados;
  pendiente.
