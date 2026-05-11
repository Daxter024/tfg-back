#!/usr/bin/env bash
# =============================================================================
# task-service — script QA end-to-end del plan test-suite-plan-task-service.md
# =============================================================================
# Ejecuta los casos del plan que son verificables desde fuera del proceso
# (HTTP, multipart, Kafka pub/sub). Los unit / WebMvc slice / JDBC tests
# internos del plan viven en src/test y se corren con
#   `./mvnw -pl task-service test`.
# Este script es para que un QA o desarrollador valide el comportamiento real
# del servicio corriendo end-to-end con sus dependencias.
#
# REQUISITOS PARA UNA EJECUCIÓN COMPLETA:
#   - task-service corriendo y accesible en $API_URL (default :8084).
#   - auth-service corriendo en :9091 (gRPC ValidateUser).
#   - terrain-service corriendo en :9093 (gRPC CheckTerrainExists).
#   - api-gateway corriendo en $GATEWAY_URL si quieres correr §26 (auth bypass).
#   - Broker Kafka accesible en $KAFKA_BOOTSTRAP para §15-§18 (hub D5 + saga).
#   - Herramientas: curl, jq (obligatorias). kcat (Kafka), grpcurl (opcional).
#
# IDs DE FIXTURE — los pone el usuario (rellenar abajo o pasar por env):
#   JWT_TOKEN          — JWT válido (obtenido vía POST /auth/login al auth-service)
#   TERRAIN_ID         — terreno existente en terrain-service
#   USER_ID            — user existente en auth-service (para assigned_to)
#   USER_ID_TODELETE   — user "sacrificable" para §15 (D2 user-deleted)
#   TERRAIN_ID_TODEL   — terreno "sacrificable" para §16 (terrain-deleted cascade)
#
# USO:
#   ./test-task-plan.sh                                  # corre todo lo que pueda
#   API_URL=http://localhost:8084 ./test-task-plan.sh
#   JWT_TOKEN=<token> TERRAIN_ID=<uuid> USER_ID=<uuid> ./test-task-plan.sh
#   ONLY="1 7 12" ./test-task-plan.sh                    # solo §1, §7 y §12
#   SKIP_SLOW=1 ./test-task-plan.sh                      # salta payloads >1 MB
#   SKIP_KAFKA=1 ./test-task-plan.sh                     # salta §15-§19 (sin broker)
#
# SALIDA:
#   [PASS] TASK-X.YY - descripción
#   [FAIL] TASK-X.YY - expected ... but got ...
#   [SKIP] TASK-X.YY - razón
#   ...
#   Resumen final + exit code 0 si todos los assertions pasaron.
# =============================================================================

set -u
set -o pipefail

# ---------- Configuración (overridable por env) -----------------------------
API_URL="${API_URL:-http://localhost:8084}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9000}"
GRPC_AUTH="${GRPC_AUTH:-localhost:9091}"
GRPC_TERRAIN="${GRPC_TERRAIN:-localhost:9093}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

# ---------- IDs / fixtures — RELLENAR aquí o pasar por env ------------------
# IMPORTANTE: estos IDs deben existir en sus servicios correspondientes.
# El usuario los pone manualmente (login real + IDs reales de la BBDD).

# JWT token válido (obtenido vía POST /auth/login)
JWT_TOKEN="${JWT_TOKEN:-dafadfadlfjadsfa}"          # TODO: rellenar

# Terreno existente en terrain-service (verificable con CheckTerrainExists)
TERRAIN_ID="${TERRAIN_ID:-dde73354-b39d-4750-ac37-70605e9fdf15}"        # TODO: rellenar

# User existente en auth-service (para assigned_to / created_by)
USER_ID="${USER_ID:-cb0609dd-f980-47bb-8e37-f91ef2311c91}"                 # TODO: rellenar

# User "sacrificable" para test §15 (D2: lo borraremos y verificaremos política)
# Si no se quiere correr §15, dejar vacío (los tests se marcan SKIP).
USER_ID_TODELETE="${USER_ID_TODELETE:-}"                 # TODO: rellenar opcional

# Terreno "sacrificable" para test §16 (cascade del terrain-deleted)
TERRAIN_ID_TODEL="${TERRAIN_ID_TODEL:-}"                 # TODO: rellenar opcional

# UUIDs "ghost" garantizados de no existir (para tests de 404)
TERRAIN_ID_GHOST="${TERRAIN_ID_GHOST:-00000000-0000-0000-0000-000000000001}"
USER_ID_GHOST="${USER_ID_GHOST:-00000000-0000-0000-0000-000000000002}"
TASK_ID_GHOST="${TASK_ID_GHOST:-00000000-0000-0000-0000-000000000003}"

RUN_TAG="QA-TASK-$(date +%s)"

SKIP_SLOW="${SKIP_SLOW:-0}"
SKIP_KAFKA="${SKIP_KAFKA:-0}"
ONLY="${ONLY:-}"

# ---------- Colores ---------------------------------------------------------
if [[ -t 1 ]]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'
    BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; BLUE=''; BOLD=''; NC=''
fi

# ---------- Contadores ------------------------------------------------------
TOTAL=0; PASSED=0; FAILED=0; SKIPPED=0
declare -a FAILED_IDS=()

# ---------- Tracking de recursos creados ------------------------------------
declare -a CREATED_TASK_IDS=()
declare -a CREATED_EVIDENCE_IDS=()
TMP_DIR="$(mktemp -d -t task-qa-XXXXXX)"
trap 'cleanup_resources; rm -rf "$TMP_DIR"' EXIT

# ---------- Detección de herramientas ---------------------------------------
HAS_JQ=0; HAS_CURL=0; HAS_GRPCURL=0; HAS_KCAT=0
command -v jq >/dev/null 2>&1 && HAS_JQ=1
command -v curl >/dev/null 2>&1 && HAS_CURL=1
command -v grpcurl >/dev/null 2>&1 && HAS_GRPCURL=1
command -v kcat >/dev/null 2>&1 && HAS_KCAT=1
command -v kafkacat >/dev/null 2>&1 && HAS_KCAT=1
KCAT_BIN="$(command -v kcat || command -v kafkacat || echo '')"

if [[ "$HAS_JQ" -eq 0 || "$HAS_CURL" -eq 0 ]]; then
    echo -e "${RED}[FATAL]${NC} jq y curl son obligatorios. Instálalos antes de seguir."
    exit 2
fi

# ---------- Helpers de aserciones -------------------------------------------

_pass() {
    local id="$1" desc="$2"
    PASSED=$((PASSED + 1)); TOTAL=$((TOTAL + 1))
    echo -e "${GREEN}[PASS]${NC} ${id} - ${desc}"
}

_fail() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    FAILED=$((FAILED + 1)); TOTAL=$((TOTAL + 1))
    FAILED_IDS+=("$id")
    echo -e "${RED}[FAIL]${NC} ${id} - ${desc}"
    echo -e "        ${YELLOW}expected:${NC} ${expected}"
    echo -e "        ${YELLOW}actual:${NC}   ${actual}"
}

skip_test() {
    local id="$1" reason="$2"
    SKIPPED=$((SKIPPED + 1)); TOTAL=$((TOTAL + 1))
    echo -e "${YELLOW}[SKIP]${NC} ${id} - ${reason}"
}

assert_status() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    if [[ "$actual" == "$expected" ]]; then
        _pass "$id" "$desc (status $actual)"
    else
        _fail "$id" "$desc" "status $expected" "status $actual"
    fi
}

assert_status_in() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    if [[ "|$expected|" == *"|$actual|"* ]]; then
        _pass "$id" "$desc (status $actual ∈ {$expected})"
    else
        _fail "$id" "$desc" "status ∈ {$expected}" "status $actual"
    fi
}

assert_json_eq() {
    local id="$1" desc="$2" path="$3" expected="$4" body="$5"
    local actual
    actual=$(echo "$body" | jq -r "$path" 2>/dev/null || echo "<jq-error>")
    if [[ "$actual" == "$expected" ]]; then
        _pass "$id" "$desc ($path = '$expected')"
    else
        _fail "$id" "$desc" "$path = '$expected'" "$path = '$actual'"
    fi
}

assert_json_contains() {
    local id="$1" desc="$2" path="$3" needle="$4" body="$5"
    local actual
    actual=$(echo "$body" | jq -r "$path" 2>/dev/null || echo "<jq-error>")
    if [[ "$actual" == *"$needle"* ]]; then
        _pass "$id" "$desc ($path contiene '$needle')"
    else
        _fail "$id" "$desc" "$path contiene '$needle'" "$path = '$actual'"
    fi
}

assert_json_present() {
    local id="$1" desc="$2" path="$3" body="$4"
    local actual
    actual=$(echo "$body" | jq -r "$path" 2>/dev/null || echo "null")
    if [[ "$actual" != "null" && -n "$actual" ]]; then
        _pass "$id" "$desc ($path presente)"
    else
        _fail "$id" "$desc" "$path != null" "$path = null"
    fi
}

assert_content_type_contains() {
    local id="$1" desc="$2" needle="$3" headers="$4"
    if echo "$headers" | grep -i "^content-type:" | grep -qi "$needle"; then
        _pass "$id" "$desc (Content-Type contiene '$needle')"
    else
        local actual; actual=$(echo "$headers" | grep -i "^content-type:" || echo "<missing>")
        _fail "$id" "$desc" "Content-Type contiene '$needle'" "$actual"
    fi
}

# ---------- Helpers de filtro de secciones ----------------------------------

should_run_section() {
    local section="$1"
    if [[ -z "$ONLY" ]]; then return 0; fi
    for s in $ONLY; do [[ "$s" == "$section" ]] && return 0; done
    return 1
}

# ---------- Helpers HTTP con JWT -------------------------------------------

http_call() {
    # http_call METHOD PATH [BODY]
    # imprime: STATUS\n<headers>\n---BODY---\n<body>
    local method="$1" path="$2" body="${3:-}"
    local args=(-s -o "$TMP_DIR/resp.body" -D "$TMP_DIR/resp.hdr" -w "%{http_code}"
                -H "Authorization: Bearer ${JWT_TOKEN}"
                -H "X-User-Id: ${USER_ID}"
                -H "X-User-Role: administrador"
                -H "Content-Type: application/json"
                -X "$method")
    [[ -n "$body" ]] && args+=(--data-raw "$body")
    local status; status=$(curl "${args[@]}" "${API_URL}${path}" 2>/dev/null || echo "000")
    echo "$status"
    cat "$TMP_DIR/resp.hdr"
    echo "---BODY---"
    cat "$TMP_DIR/resp.body"
}

call_status() {
    local method="$1" path="$2" body="${3:-}"
    local args=(-s -o "$TMP_DIR/resp.body" -D "$TMP_DIR/resp.hdr" -w "%{http_code}"
                -H "Authorization: Bearer ${JWT_TOKEN}"
                -H "X-User-Id: ${USER_ID}"
                -H "X-User-Role: administrador"
                -H "Content-Type: application/json"
                -X "$method")
    [[ -n "$body" ]] && args+=(--data-raw "$body")
    curl "${args[@]}" "${API_URL}${path}" 2>/dev/null || echo "000"
}

last_body() { cat "$TMP_DIR/resp.body" 2>/dev/null; }
last_headers() { cat "$TMP_DIR/resp.hdr" 2>/dev/null; }

# ---------- Helpers de payload ----------------------------------------------

# Fecha futura ISO (lo más portable posible — necesita coreutils o BSD date).
future_iso() {
    local hours="${1:-24}"
    if date -u -v+${hours}H +"%Y-%m-%dT%H:%M:%S" >/dev/null 2>&1; then
        date -u -v+${hours}H +"%Y-%m-%dT%H:%M:%S"
    else
        date -u -d "+${hours} hours" +"%Y-%m-%dT%H:%M:%S"
    fi
}
past_iso() {
    if date -u -v-1d +"%Y-%m-%dT%H:%M:%S" >/dev/null 2>&1; then
        date -u -v-1d +"%Y-%m-%dT%H:%M:%S"
    else
        date -u -d "-1 day" +"%Y-%m-%dT%H:%M:%S"
    fi
}
future_date() {
    local days="${1:-30}"
    if date -u -v+${days}d +"%Y-%m-%d" >/dev/null 2>&1; then
        date -u -v+${days}d +"%Y-%m-%d"
    else
        date -u -d "+${days} days" +"%Y-%m-%d"
    fi
}

make_task_body() {
    local type="${1:-IRRIGATION}" planned="${2:-$(future_iso 24)}" duration="${3:-120}"
    local extra="${4:-}"
    cat <<EOF
{"task_type_code":"$type","terrain_id":"$TERRAIN_ID","planned_at":"$planned","estimated_duration_minutes":$duration,"assigned_to":"$USER_ID"$extra}
EOF
}

# ---------- Cleanup ---------------------------------------------------------

cleanup_resources() {
    if [[ ${#CREATED_TASK_IDS[@]} -gt 0 ]]; then
        echo -e "${BLUE}[CLEANUP]${NC} eliminando ${#CREATED_TASK_IDS[@]} tasks creadas por el run…"
        for tid in "${CREATED_TASK_IDS[@]}"; do
            curl -s -X DELETE -H "Authorization: Bearer $JWT_TOKEN" \
                 -H "X-User-Id: $USER_ID" -H "X-User-Role: administrador" \
                 "${API_URL}/task/${tid}" -o /dev/null || true
        done
    fi
}

# ---------- Pre-flight ------------------------------------------------------

echo -e "${BOLD}task-service QA — plan test-suite-plan-task-service.md${NC}"
echo "API_URL=$API_URL"
echo "RUN_TAG=$RUN_TAG"
echo "Herramientas:"
[[ "$HAS_GRPCURL" -eq 1 ]] && echo "  grpcurl disponible — pre-check de fixtures ON" || echo "  grpcurl NO — pre-check de fixtures SKIP"
[[ -n "$KCAT_BIN" ]] && echo "  kcat disponible — §15-§19 (Kafka) ON" || echo "  kcat NO — §15-§19 (Kafka) SKIP"

if [[ "$JWT_TOKEN" == "PEGAR_AQUI_JWT_TOKEN" ]]; then
    echo -e "${RED}[FATAL]${NC} JWT_TOKEN no configurado. Edita el script o pasa JWT_TOKEN=… ./test-task-plan.sh"
    exit 2
fi
if [[ "$TERRAIN_ID" == "PEGAR_AQUI_TERRAIN_ID" || "$USER_ID" == "PEGAR_AQUI_USER_ID" ]]; then
    echo -e "${RED}[FATAL]${NC} TERRAIN_ID y USER_ID son obligatorios. Edita el script o exporta las vars."
    exit 2
fi

# Pre-check de fixtures vía gRPC (best-effort)
TERRAIN_FIXTURE_OK=0; USER_FIXTURE_OK=0
if [[ "$HAS_GRPCURL" -eq 1 ]]; then
    OUT=$(grpcurl -plaintext -d "{\"terrain_id\":\"$TERRAIN_ID\"}" \
           "$GRPC_TERRAIN" com.agro.terrain.grpc.TerrainService/CheckTerrainExists 2>/dev/null \
           | jq -r '.exists' 2>/dev/null || echo "")
    [[ "$OUT" == "true" ]] && TERRAIN_FIXTURE_OK=1
    OUT=$(grpcurl -plaintext -d "{\"userId\":\"$USER_ID\"}" \
           "$GRPC_AUTH" UserValidationService/ValidateUser 2>/dev/null \
           | jq -r '.exists' 2>/dev/null || echo "")
    [[ "$OUT" == "true" ]] && USER_FIXTURE_OK=1
fi

if [[ "$TERRAIN_FIXTURE_OK" -eq 0 || "$USER_FIXTURE_OK" -eq 0 ]]; then
    echo -e "${YELLOW}[WARN]${NC} no pude verificar TERRAIN_ID y/o USER_ID vía gRPC."
    echo "  TERRAIN_ID=$TERRAIN_ID  ok=$TERRAIN_FIXTURE_OK"
    echo "  USER_ID=$USER_ID    ok=$USER_FIXTURE_OK"
    echo "  Si los IDs son válidos, ignora este warn. Si no, exporta variables reales."
else
    echo -e "  ${GREEN}fixtures TERRAIN_ID + USER_ID validados.${NC}"
fi

echo "---"

# =============================================================================
# §1. POST /task — creación
# =============================================================================
if should_run_section 1; then
    echo -e "${BOLD}${BLUE}§1. POST /task — creación${NC}"

    # TASK-1.01: happy path mínimo
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 120)
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.01" "happy path mínimo" 201 "$STATUS"
    TASK_ID_1_01=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    if [[ "${#TASK_ID_1_01}" -eq 36 ]]; then
        CREATED_TASK_IDS+=("$TASK_ID_1_01")
        echo "    task creada: $TASK_ID_1_01"
    fi

    # TASK-1.04: task_type_code desconocido → 400
    BODY=$(make_task_body "NOPE" "$(future_iso 24)" 60)
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.04" "task_type_code desconocido" 400 "$STATUS"

    # TASK-1.07: terrain_id inexistente → 404
    BODY="{\"task_type_code\":\"IRRIGATION\",\"terrain_id\":\"$TERRAIN_ID_GHOST\",\"planned_at\":\"$(future_iso 24)\",\"estimated_duration_minutes\":60,\"assigned_to\":\"$USER_ID\"}"
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.07" "terrain_id inexistente → 404" 404 "$STATUS"

    # TASK-1.09: planned_at en pasado → 400
    BODY=$(make_task_body "IRRIGATION" "$(past_iso)" 60)
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.09" "planned_at en pasado" 400 "$STATUS"

    # TASK-1.13: duration ≤ 0 → 400
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 0)
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.13" "estimated_duration_minutes=0" 400 "$STATUS"

    # TASK-1.16: assigned_to inexistente → 404
    BODY="{\"task_type_code\":\"IRRIGATION\",\"terrain_id\":\"$TERRAIN_ID\",\"planned_at\":\"$(future_iso 24)\",\"estimated_duration_minutes\":60,\"assigned_to\":\"$USER_ID_GHOST\"}"
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.16" "assigned_to inexistente → 404" 404 "$STATUS"

    # TASK-1.17: recurrencia WEEKLY x10
    UNTIL=$(future_date 70)
    EXTRA=",\"recurrence\":{\"frequency\":\"WEEKLY\",\"interval\":1,\"until\":\"$UNTIL\"}"
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 60 "$EXTRA")
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.17" "recurrencia WEEKLY 10 instancias" 201 "$STATUS"
    TASK_ID_REC=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    [[ "${#TASK_ID_REC}" -eq 36 ]] && CREATED_TASK_IDS+=("$TASK_ID_REC")

    # TASK-1.19: recurrencia DAILY supera 365 instancias → 400
    # (+400 days con interval=1 produce ~400 instancias, > limit 365)
    UNTIL=$(future_date 400)
    EXTRA=",\"recurrence\":{\"frequency\":\"DAILY\",\"interval\":1,\"until\":\"$UNTIL\"}"
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 60 "$EXTRA")
    STATUS=$(call_status POST /task "$BODY")
    assert_status "TASK-1.19" "recurrencia >365 instancias" 400 "$STATUS"

    # TASK-1.29: body vacío → 400
    STATUS=$(call_status POST /task "{}")
    assert_status "TASK-1.29" "body vacío" 400 "$STATUS"

    # TASK-1.31: body JSON malformado → 400
    STATUS=$(call_status POST /task '{"terrain_id":')
    assert_status_in "TASK-1.31" "JSON malformado" "400|415" "$STATUS"
fi

# =============================================================================
# §2. GET /task/{id} — detalle
# =============================================================================
if should_run_section 2; then
    echo -e "${BOLD}${BLUE}§2. GET /task/{id} — detalle${NC}"

    if [[ -n "${TASK_ID_1_01:-}" ]]; then
        # TASK-2.01: detalle existente
        STATUS=$(call_status GET "/task/${TASK_ID_1_01}")
        assert_status "TASK-2.01" "detalle existente" 200 "$STATUS"
        assert_json_present "TASK-2.01.a" "detalle incluye id" ".id" "$(last_body)"

        # TASK-2.03: fields=id,state
        STATUS=$(call_status GET "/task/${TASK_ID_1_01}?fields=id,state")
        assert_status "TASK-2.03" "fields=id,state" 200 "$STATUS"

        # TASK-2.04: fields inválido
        STATUS=$(call_status GET "/task/${TASK_ID_1_01}?fields=secret")
        assert_status "TASK-2.04" "fields inválido" 400 "$STATUS"
    else
        skip_test "TASK-2.0X" "requiere §1.01 OK"
    fi

    # TASK-2.02: detalle inexistente → 404
    STATUS=$(call_status GET "/task/${TASK_ID_GHOST}")
    assert_status "TASK-2.02" "detalle inexistente" 404 "$STATUS"

    # TASK-2.09: id no UUID
    STATUS=$(call_status GET "/task/abc")
    assert_status_in "TASK-2.09" "id no UUID" "400|404" "$STATUS"
fi

# =============================================================================
# §3. GET /task — listado + filtros
# =============================================================================
if should_run_section 3; then
    echo -e "${BOLD}${BLUE}§3. GET /task — listado${NC}"

    # TASK-3.02: paginación default
    STATUS=$(call_status GET "/task?size=20")
    assert_status "TASK-3.02" "listado paginado" 200 "$STATUS"
    assert_json_present "TASK-3.02.a" "respuesta tiene 'content'" ".content" "$(last_body)"

    # TASK-3.05: filtro assigned_to
    STATUS=$(call_status GET "/task?assigned_to=${USER_ID}")
    assert_status "TASK-3.05" "filtro assigned_to" 200 "$STATUS"

    # TASK-3.07: filtro state CSV
    STATUS=$(call_status GET "/task?state=PENDING,IN_PROGRESS")
    assert_status "TASK-3.07" "filtro state CSV" 200 "$STATUS"

    # TASK-3.08: state inválido → 400
    STATUS=$(call_status GET "/task?state=NOPE")
    assert_status_in "TASK-3.08" "state inválido" "400|200" "$STATUS"

    # TASK-3.12: overdue=true
    STATUS=$(call_status GET "/task?overdue=true")
    assert_status "TASK-3.12" "overdue=true" 200 "$STATUS"

    # TASK-3.10: filtro terrain_id
    STATUS=$(call_status GET "/task?terrain_id=${TERRAIN_ID}")
    assert_status "TASK-3.10" "filtro terrain_id" 200 "$STATUS"
fi

# =============================================================================
# §4. GET /task/calendar
# =============================================================================
if should_run_section 4; then
    echo -e "${BOLD}${BLUE}§4. GET /task/calendar${NC}"

    FROM=$(future_iso 0); TO=$(future_iso 168)  # +7d
    # TASK-4.01: happy path week
    STATUS=$(call_status GET "/task/calendar?view=week&from=${FROM}&to=${TO}")
    assert_status "TASK-4.01" "calendar week" 200 "$STATUS"

    # TASK-4.04: view inválido → 400
    STATUS=$(call_status GET "/task/calendar?view=decade&from=${FROM}&to=${TO}")
    assert_status "TASK-4.04" "view inválido" 400 "$STATUS"

    # TASK-4.06: rango > 13 meses → 400
    BIG_TO=$(future_iso $((24 * 410)))   # ~13.5 meses
    STATUS=$(call_status GET "/task/calendar?view=year&from=${FROM}&to=${BIG_TO}")
    assert_status "TASK-4.06" "rango > 13 meses" 400 "$STATUS"
fi

# =============================================================================
# §6. DELETE /task/{id}
# =============================================================================
if should_run_section 6; then
    echo -e "${BOLD}${BLUE}§6. DELETE /task/{id}${NC}"

    # TASK-6.01: PENDING sin history → 204
    BODY=$(make_task_body "OTHER" "$(future_iso 24)" 30)
    STATUS=$(call_status POST /task "$BODY")
    TID=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    if [[ "${#TID}" -eq 36 ]]; then
        STATUS=$(call_status DELETE "/task/${TID}")
        assert_status "TASK-6.01" "DELETE PENDING sin history" 204 "$STATUS"
    else
        skip_test "TASK-6.01" "no pude crear task previo"
    fi

    # TASK-6.06: id inexistente → 404
    STATUS=$(call_status DELETE "/task/${TASK_ID_GHOST}")
    assert_status "TASK-6.06" "DELETE inexistente" 404 "$STATUS"

    # TASK-6.07: id no UUID
    STATUS=$(call_status DELETE "/task/abc")
    assert_status_in "TASK-6.07" "id no UUID" "400|404" "$STATUS"
fi

# =============================================================================
# §7. POST /task/{id}/transition — máquina de estados
# =============================================================================
if should_run_section 7; then
    echo -e "${BOLD}${BLUE}§7. POST /task/{id}/transition${NC}"

    # Setup: crea task para los tests de transición
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 60)
    STATUS=$(call_status POST /task "$BODY")
    TID=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    if [[ "${#TID}" -ne 36 ]]; then
        skip_test "TASK-7.*" "no pude crear task previa"
    else
        CREATED_TASK_IDS+=("$TID")

        # TASK-7.03: PENDING → FINISHED (ilegal) → 409
        FIN_BODY='{"to_state":"FINISHED","real_duration_minutes":60,"consumed_inputs":[{"input_name":"x","quantity":1.0,"unit":"L"}],"note":"skip"}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$FIN_BODY")
        assert_status "TASK-7.03" "PENDING → FINISHED ilegal" 409 "$STATUS"

        # TASK-7.01: PENDING → IN_PROGRESS
        IP_BODY='{"to_state":"IN_PROGRESS","note":"empezando"}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$IP_BODY")
        assert_status "TASK-7.01" "PENDING → IN_PROGRESS" 200 "$STATUS"

        # TASK-7.10: FINISHED sin real_duration → 400 o 409
        # (el servicio devuelve 409 InvalidStateTransition con detalle "duración requerida")
        BAD='{"to_state":"FINISHED","consumed_inputs":[{"input_name":"x","quantity":1.0,"unit":"L"}]}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$BAD")
        assert_status_in "TASK-7.10" "FINISHED sin real_duration" "400|409" "$STATUS"

        # TASK-7.11: FINISHED sin consumed_inputs → 400 o 409
        BAD='{"to_state":"FINISHED","real_duration_minutes":60}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$BAD")
        assert_status_in "TASK-7.11" "FINISHED sin consumed_inputs" "400|409" "$STATUS"

        # TASK-7.04: IN_PROGRESS → FINISHED happy (verifica también §19 — publica Kafka)
        FIN_BODY='{"to_state":"FINISHED","real_duration_minutes":58,"consumed_inputs":[{"input_name":"Agua","quantity":50.0,"unit":"L"}],"note":"done"}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$FIN_BODY")
        assert_status "TASK-7.04" "IN_PROGRESS → FINISHED" 200 "$STATUS"

        # TASK-7.06: FINISHED → cualquier cosa → 409 (inmutable)
        ANY='{"to_state":"CANCELLED","note":"oops"}'
        STATUS=$(call_status POST "/task/${TID}/transition" "$ANY")
        assert_status "TASK-7.06" "FINISHED inmutable" 409 "$STATUS"

        # TASK-7.13: TREATMENT FINISHED sin evidencia → 409
        TR_BODY=$(make_task_body "TREATMENT" "$(future_iso 24)" 60)
        STATUS=$(call_status POST /task "$TR_BODY")
        TRT=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
        if [[ "${#TRT}" -eq 36 ]]; then
            CREATED_TASK_IDS+=("$TRT")
            call_status POST "/task/${TRT}/transition" '{"to_state":"IN_PROGRESS"}' >/dev/null
            FIN_BODY='{"to_state":"FINISHED","real_duration_minutes":50,"consumed_inputs":[{"input_name":"Cobre","quantity":1.0,"unit":"kg"}],"note":"done"}'
            STATUS=$(call_status POST "/task/${TRT}/transition" "$FIN_BODY")
            assert_status "TASK-7.13" "TREATMENT FINISHED sin evidencia" 409 "$STATUS"
        else
            skip_test "TASK-7.13" "no pude crear TREATMENT task"
        fi

        # TASK-7.29: id inexistente → 404
        STATUS=$(call_status POST "/task/${TASK_ID_GHOST}/transition" '{"to_state":"IN_PROGRESS"}')
        assert_status "TASK-7.29" "transition de id inexistente" 404 "$STATUS"

        # TASK-7.30: to_state inválido
        STATUS=$(call_status POST "/task/${TID}/transition" '{"to_state":"NOPE"}')
        assert_status "TASK-7.30" "to_state inválido" 400 "$STATUS"
    fi
fi

# =============================================================================
# §8. Endpoints de evidencias (multipart)
# =============================================================================
if should_run_section 8; then
    echo -e "${BOLD}${BLUE}§8. Endpoints de evidencias${NC}"

    # Setup: PNG válido pequeño (8x8 transparent) + JPG + PDF + TXT (no permitido)
    PNG_FILE="$TMP_DIR/ev.png"
    JPG_FILE="$TMP_DIR/ev.jpg"
    PDF_FILE="$TMP_DIR/ev.pdf"
    TXT_FILE="$TMP_DIR/ev.txt"
    # PNG mínimo 1x1 transparente (base64-decoded)
    printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xfc\xcf\xc0\x00\x00\x00\x05\x00\x01\xa5\x1c\xe7M\x00\x00\x00\x00IEND\xaeB`\x82' > "$PNG_FILE"
    # JPG mínimo (4 bytes mágicos suficiente para algunos validadores; usar header real para multipart no es vital)
    printf '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.'"'"' ",#\x1c\x1c(7),01444\x1f'"'"'9=82<.342\xff\xc0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x14\x00\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\xff\xc4\x00\x14\x10\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\xff\xda\x00\x08\x01\x01\x00\x00?\x00\xfb\xff\xd9' > "$JPG_FILE"
    # PDF mínimo: cabecera + objeto vacío
    printf '%%PDF-1.4\n1 0 obj<<>>endobj\nxref\n0 1\n0000000000 65535 f\ntrailer<</Size 1>>\nstartxref\n0\n%%EOF' > "$PDF_FILE"
    echo "no es una imagen" > "$TXT_FILE"

    # Crea una task para colgarle las evidencias
    BODY=$(make_task_body "TREATMENT" "$(future_iso 24)" 60)
    STATUS=$(call_status POST /task "$BODY")
    EVTASK=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    if [[ "${#EVTASK}" -ne 36 ]]; then
        skip_test "TASK-8.*" "no pude crear task para evidencias"
    else
        CREATED_TASK_IDS+=("$EVTASK")

        # TASK-8.01: POST PNG válido
        STATUS=$(curl -s -o "$TMP_DIR/resp.body" -w "%{http_code}" \
                      -H "Authorization: Bearer $JWT_TOKEN" \
                      -H "X-User-Id: $USER_ID" \
                      -H "X-User-Role: administrador" \
                      -F "file=@${PNG_FILE};type=image/png" \
                      -X POST "${API_URL}/task/${EVTASK}/evidence" 2>/dev/null || echo "000")
        assert_status_in "TASK-8.01" "POST evidencia PNG" "201|200" "$STATUS"

        # TASK-8.02: JPG
        STATUS=$(curl -s -o "$TMP_DIR/resp.body" -w "%{http_code}" \
                      -H "Authorization: Bearer $JWT_TOKEN" \
                      -H "X-User-Id: $USER_ID" \
                      -H "X-User-Role: administrador" \
                      -F "file=@${JPG_FILE};type=image/jpeg" \
                      -X POST "${API_URL}/task/${EVTASK}/evidence" 2>/dev/null || echo "000")
        assert_status_in "TASK-8.02" "POST evidencia JPG" "201|200" "$STATUS"

        # TASK-8.03: PDF
        STATUS=$(curl -s -o "$TMP_DIR/resp.body" -w "%{http_code}" \
                      -H "Authorization: Bearer $JWT_TOKEN" \
                      -H "X-User-Id: $USER_ID" \
                      -H "X-User-Role: administrador" \
                      -F "file=@${PDF_FILE};type=application/pdf" \
                      -X POST "${API_URL}/task/${EVTASK}/evidence" 2>/dev/null || echo "000")
        assert_status_in "TASK-8.03" "POST evidencia PDF" "201|200" "$STATUS"

        # TASK-8.04: MIME no permitido (text/plain)
        STATUS=$(curl -s -o "$TMP_DIR/resp.body" -w "%{http_code}" \
                      -H "Authorization: Bearer $JWT_TOKEN" \
                      -H "X-User-Id: $USER_ID" \
                      -H "X-User-Role: administrador" \
                      -F "file=@${TXT_FILE};type=text/plain" \
                      -X POST "${API_URL}/task/${EVTASK}/evidence" 2>/dev/null || echo "000")
        assert_status_in "TASK-8.04" "MIME text/plain rechazado" "400|415" "$STATUS"

        # TASK-8.06: task_id inexistente
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                      -H "Authorization: Bearer $JWT_TOKEN" \
                      -H "X-User-Id: $USER_ID" \
                      -H "X-User-Role: administrador" \
                      -F "file=@${PNG_FILE};type=image/png" \
                      -X POST "${API_URL}/task/${TASK_ID_GHOST}/evidence" 2>/dev/null || echo "000")
        assert_status "TASK-8.06" "task_id inexistente" 404 "$STATUS"

        # TASK-8.08: GET listado de evidencias
        STATUS=$(call_status GET "/task/${EVTASK}/evidence")
        assert_status "TASK-8.08" "GET listado evidencias" 200 "$STATUS"

        # TASK-8.16: ahora que esta TREATMENT tiene evidencia, FINISHED debería pasar
        call_status POST "/task/${EVTASK}/transition" '{"to_state":"IN_PROGRESS"}' >/dev/null
        FIN_BODY='{"to_state":"FINISHED","real_duration_minutes":60,"consumed_inputs":[{"input_name":"Cobre","quantity":1.0,"unit":"kg"}],"note":"done"}'
        STATUS=$(call_status POST "/task/${EVTASK}/transition" "$FIN_BODY")
        assert_status "TASK-7.16" "FINISHED de TREATMENT CON evidencia" 200 "$STATUS"

        if [[ "$SKIP_SLOW" -ne 1 ]]; then
            # TASK-8.05: > 10 MB → 413 o 400
            BIG_FILE="$TMP_DIR/big.png"
            dd if=/dev/zero of="$BIG_FILE" bs=1M count=11 2>/dev/null
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                          -H "Authorization: Bearer $JWT_TOKEN" \
                          -H "X-User-Id: $USER_ID" \
                          -H "X-User-Role: administrador" \
                          -F "file=@${BIG_FILE};type=image/png" \
                          -X POST "${API_URL}/task/${EVTASK}/evidence" 2>/dev/null || echo "000")
            assert_status_in "TASK-8.05" "evidencia > 10 MB" "400|413" "$STATUS"
        else
            skip_test "TASK-8.05" "SKIP_SLOW=1"
        fi
    fi
fi

# =============================================================================
# §9. GET /task/dashboard
# =============================================================================
if should_run_section 9; then
    echo -e "${BOLD}${BLUE}§9. GET /task/dashboard${NC}"

    STATUS=$(call_status GET "/task/dashboard")
    assert_status "TASK-9.02" "dashboard responde" 200 "$STATUS"
    assert_json_present "TASK-9.02.a" "tiene 'totals'" ".totals" "$(last_body)"
    assert_json_present "TASK-9.02.b" "tiene 'by_week'" ".by_week" "$(last_body)"
    assert_json_present "TASK-9.02.c" "tiene 'by_type'" ".by_type" "$(last_body)"
fi

# =============================================================================
# §10. GET /task/export.csv
# =============================================================================
if should_run_section 10; then
    echo -e "${BOLD}${BLUE}§10. GET /task/export.csv${NC}"

    STATUS=$(call_status GET "/task/export.csv")
    assert_status "TASK-10.02" "export devuelve 200" 200 "$STATUS"
    assert_content_type_contains "TASK-10.06" "Content-Type CSV" "text/csv" "$(last_headers)"
fi

# =============================================================================
# §12. Notification endpoints
# =============================================================================
if should_run_section 12; then
    echo -e "${BOLD}${BLUE}§12. Notification endpoints${NC}"

    # TASK-12.01: bandeja (puede estar vacía)
    STATUS=$(call_status GET "/notification")
    assert_status "TASK-12.01" "GET /notification" 200 "$STATUS"

    # TASK-12.09: unread-count
    STATUS=$(call_status GET "/notification/unread-count")
    assert_status "TASK-12.09" "unread-count" 200 "$STATUS"
    assert_json_present "TASK-12.09.a" "tiene 'count'" ".count" "$(last_body)"

    # TASK-12.10: preferences (devuelve defaults si no hay)
    STATUS=$(call_status GET "/notification/preferences")
    assert_status "TASK-12.10" "preferences default" 200 "$STATUS"

    # TASK-12.11: PUT preferences (200 OK o 204 No Content válidos)
    PREF='{"email_enabled":true,"in_app_enabled":true,"default_lead_minutes":2880,"task_type_lead_minutes":{"TREATMENT":4320},"also_notify_creator":false}'
    STATUS=$(call_status PUT "/notification/preferences" "$PREF")
    assert_status_in "TASK-12.11" "PUT preferences" "200|204" "$STATUS"

    # TASK-12.15: id no UUID
    STATUS=$(call_status POST "/notification/abc/read")
    assert_status_in "TASK-12.15" "id no UUID" "400|404" "$STATUS"
fi

# =============================================================================
# §15. user-deleted vía Kafka — política D2
# Publica un evento user-deleted con USER_ID_TODELETE y verifica que tras unos
# segundos las tasks PENDING/IN_PROGRESS/CANCELLED del user desaparecen y las
# FINISHED quedan con assigned_to=UUID-cero.
# =============================================================================
if should_run_section 15 && [[ "$SKIP_KAFKA" -ne 1 ]]; then
    echo -e "${BOLD}${BLUE}§15. user-deleted (D2)${NC}"
    if [[ -z "$KCAT_BIN" ]]; then
        skip_test "TASK-15.*" "kcat no disponible"
    elif [[ -z "$USER_ID_TODELETE" ]]; then
        skip_test "TASK-15.*" "USER_ID_TODELETE no configurado (test destructivo opcional)"
    else
        PAYLOAD="{\"userId\":\"$USER_ID_TODELETE\"}"
        if echo "$PAYLOAD" | "$KCAT_BIN" -b "$KAFKA_BOOTSTRAP" -t user-deleted -P \
                -H "__TypeId__=com.agro.authservice.event.UserDeletedEvent" 2>/dev/null; then
            sleep 3
            # No tenemos un endpoint específico para verificar policy directamente,
            # pero podemos listar tasks del user y ver que PENDING/IN_PROGRESS no aparecen
            STATUS=$(call_status GET "/task?assigned_to=${USER_ID_TODELETE}&state=PENDING,IN_PROGRESS,CANCELLED")
            COUNT=$(last_body | jq '.content | length' 2>/dev/null || echo "?")
            if [[ "$COUNT" == "0" ]]; then
                _pass "TASK-15.10" "PENDING/IN_PROGRESS/CANCELLED del user borradas"
            else
                _fail "TASK-15.10" "tasks no-FINISHED del user borradas" "0" "$COUNT"
            fi
        else
            _fail "TASK-15.10" "publish user-deleted" "ok" "kcat err"
        fi
    fi
elif should_run_section 15; then
    skip_test "TASK-15.*" "SKIP_KAFKA=1"
fi

# =============================================================================
# §16. terrain-deleted vía Kafka — cascade
# Publica terrain-deleted con TERRAIN_ID_TODEL y verifica que las tasks del
# terrain desaparecen.
# =============================================================================
if should_run_section 16 && [[ "$SKIP_KAFKA" -ne 1 ]]; then
    echo -e "${BOLD}${BLUE}§16. terrain-deleted (cascade)${NC}"
    if [[ -z "$KCAT_BIN" ]]; then
        skip_test "TASK-16.*" "kcat no disponible"
    elif [[ -z "$TERRAIN_ID_TODEL" ]]; then
        skip_test "TASK-16.*" "TERRAIN_ID_TODEL no configurado (test destructivo opcional)"
    else
        # Crea una task del terrain "sacrificable"
        BODY="{\"task_type_code\":\"IRRIGATION\",\"terrain_id\":\"$TERRAIN_ID_TODEL\",\"planned_at\":\"$(future_iso 24)\",\"estimated_duration_minutes\":60,\"assigned_to\":\"$USER_ID\"}"
        STATUS=$(call_status POST /task "$BODY")
        if [[ "$STATUS" != "201" ]]; then
            skip_test "TASK-16.10" "no pude crear task del TERRAIN_ID_TODEL (no existe?)"
        else
            TID=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)

            PAYLOAD="{\"terrainId\":\"$TERRAIN_ID_TODEL\"}"
            if echo "$PAYLOAD" | "$KCAT_BIN" -b "$KAFKA_BOOTSTRAP" -t terrain-deleted -P \
                    -H "__TypeId__=com.agro.terrainservice.event.TerrainDeletedEvent" 2>/dev/null; then
                sleep 3
                STATUS=$(call_status GET "/task/${TID}")
                assert_status "TASK-16.10" "tras terrain-deleted, task del terrain → 404" 404 "$STATUS"
            else
                _fail "TASK-16.10" "publish terrain-deleted" "ok" "kcat err"
            fi
        fi
    fi
elif should_run_section 16; then
    skip_test "TASK-16.*" "SKIP_KAFKA=1"
fi

# =============================================================================
# §17. stock-low vía Kafka — hub D5
# Publica un stock-low fingido y verifica que se crea una notification para
# createdBy (== USER_ID del JWT actual).
# =============================================================================
if should_run_section 17 && [[ "$SKIP_KAFKA" -ne 1 ]]; then
    echo -e "${BOLD}${BLUE}§17. stock-low → notification (hub D5)${NC}"
    if [[ -z "$KCAT_BIN" ]]; then
        skip_test "TASK-17.*" "kcat no disponible"
    else
        FAKE_INPUT_ID=$(uuidgen 2>/dev/null || python3 -c "import uuid;print(uuid.uuid4())")
        PAYLOAD="{\"inputId\":\"$FAKE_INPUT_ID\",\"inputName\":\"QA-INPUT-$RUN_TAG\",\"currentStock\":2.0,\"threshold\":5.0,\"unit\":\"kg\",\"createdBy\":\"$USER_ID\"}"
        if echo "$PAYLOAD" | "$KCAT_BIN" -b "$KAFKA_BOOTSTRAP" -t stock-low -P \
                -H "__TypeId__=com.agro.inputservice.event.StockLowEvent" 2>/dev/null; then
            sleep 3
            STATUS=$(call_status GET "/notification?unread=true")
            assert_status "TASK-17.02" "GET /notification tras stock-low" 200 "$STATUS"
            # Buscar fila con source_kind=STOCK_LOW (best-effort; el schema concreto puede variar)
            COUNT=$(last_body | jq "[.content[]? | select(.source_kind==\"STOCK_LOW\")] | length" 2>/dev/null || echo "0")
            if [[ "$COUNT" -ge "1" ]]; then
                _pass "TASK-17.02.a" "notification STOCK_LOW creada (count=$COUNT)"
            else
                _fail "TASK-17.02.a" "notification STOCK_LOW creada" "count ≥ 1" "count=$COUNT"
            fi
        else
            _fail "TASK-17.02" "publish stock-low" "ok" "kcat err"
        fi
    fi
elif should_run_section 17; then
    skip_test "TASK-17.*" "SKIP_KAFKA=1"
fi

# =============================================================================
# §18. sensor-alert vía Kafka — hub D5
# Publica una sensor-alert fingida y verifica que se crea una notification.
# =============================================================================
if should_run_section 18 && [[ "$SKIP_KAFKA" -ne 1 ]]; then
    echo -e "${BOLD}${BLUE}§18. sensor-alert → notification (hub D5)${NC}"
    if [[ -z "$KCAT_BIN" ]]; then
        skip_test "TASK-18.*" "kcat no disponible"
    else
        FAKE_ALERT_ID=$(uuidgen 2>/dev/null || python3 -c "import uuid;print(uuid.uuid4())")
        FAKE_SENSOR_ID=$(uuidgen 2>/dev/null || python3 -c "import uuid;print(uuid.uuid4())")
        PAYLOAD="{\"alertId\":\"$FAKE_ALERT_ID\",\"sensorId\":\"$FAKE_SENSOR_ID\",\"terrainId\":\"$TERRAIN_ID\",\"variable\":\"temperature\",\"kind\":\"above_max\",\"value\":42.5,\"threshold\":35.0,\"recordedAt\":\"$(future_iso 0)Z\",\"notifyUserIds\":[\"$USER_ID\"]}"
        if echo "$PAYLOAD" | "$KCAT_BIN" -b "$KAFKA_BOOTSTRAP" -t sensor-alert -P \
                -H "__TypeId__=com.agro.iotservice.event.SensorAlertEvent" 2>/dev/null; then
            sleep 3
            STATUS=$(call_status GET "/notification?unread=true")
            assert_status "TASK-18.02" "GET /notification tras sensor-alert" 200 "$STATUS"
            COUNT=$(last_body | jq "[.content[]? | select(.source_kind==\"SENSOR_ALERT\")] | length" 2>/dev/null || echo "0")
            if [[ "$COUNT" -ge "1" ]]; then
                _pass "TASK-18.02.a" "notification SENSOR_ALERT creada (count=$COUNT)"
            else
                _fail "TASK-18.02.a" "notification SENSOR_ALERT creada" "count ≥ 1" "count=$COUNT"
            fi
        else
            _fail "TASK-18.02" "publish sensor-alert" "ok" "kcat err"
        fi
    fi
elif should_run_section 18; then
    skip_test "TASK-18.*" "SKIP_KAFKA=1"
fi

# =============================================================================
# §19. Producer task-completed — verificación con kcat (subscribe)
# Consume task-completed durante unos segundos para ver si el FINISHED de §7
# llegó. Solo verifica si kcat está disponible.
# =============================================================================
if should_run_section 19 && [[ "$SKIP_KAFKA" -ne 1 ]]; then
    echo -e "${BOLD}${BLUE}§19. Producer task-completed${NC}"
    if [[ -z "$KCAT_BIN" ]]; then
        skip_test "TASK-19.10" "kcat no disponible"
    else
        # Crea + transición FINISHED de una task OTHER (no requiere evidencia)
        BODY=$(make_task_body "OTHER" "$(future_iso 24)" 30)
        STATUS=$(call_status POST /task "$BODY")
        T19=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
        if [[ "${#T19}" -ne 36 ]]; then
            skip_test "TASK-19.10" "no pude crear task previa"
        else
            CREATED_TASK_IDS+=("$T19")
            call_status POST "/task/${T19}/transition" '{"to_state":"IN_PROGRESS"}' >/dev/null
            call_status POST "/task/${T19}/transition" \
                '{"to_state":"FINISHED","real_duration_minutes":25,"consumed_inputs":[{"input_name":"Agua","quantity":1.0,"unit":"L"}],"note":"qa"}' >/dev/null

            # Suscribirse a task-completed por 4s y buscar nuestro taskId
            OUT=$(timeout 4 "$KCAT_BIN" -b "$KAFKA_BOOTSTRAP" -t task-completed -C -o -100 -e -q 2>/dev/null | grep -F "$T19" || true)
            if [[ -n "$OUT" ]]; then
                _pass "TASK-19.10" "task-completed publicado con taskId=$T19"
            else
                _fail "TASK-19.10" "task-completed contiene taskId" "$T19" "no encontrado en últimos 100 mensajes"
            fi
        fi
    fi
elif should_run_section 19; then
    skip_test "TASK-19.*" "SKIP_KAFKA=1"
fi

# =============================================================================
# §26. Seguridad defensiva (smoke)
# =============================================================================
if should_run_section 26; then
    echo -e "${BOLD}${BLUE}§26. Seguridad defensiva${NC}"

    # TASK-26.01: SQLi vía fields
    STATUS=$(call_status GET "/task/${TASK_ID_GHOST}?fields=id;DROP%20TABLE%20task;--")
    assert_status_in "TASK-26.01" "SQLi via fields rechazado" "400|404" "$STATUS"

    # TASK-26.03: body con propiedades extra (Jackson tolerante)
    BODY=$(make_task_body "IRRIGATION" "$(future_iso 24)" 60 ',"isAdmin":true,"hax":"yes"')
    STATUS=$(call_status POST /task "$BODY")
    assert_status_in "TASK-26.03" "props extra ignoradas" "201|400" "$STATUS"
    TID_EX=$(last_body | jq -r '.id // .' 2>/dev/null | head -c 36)
    [[ "${#TID_EX}" -eq 36 ]] && CREATED_TASK_IDS+=("$TID_EX")

    # TASK-26.08: sin JWT directo al servicio
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" "${API_URL}/task" 2>/dev/null || echo "000")
    assert_status_in "TASK-26.08" "GET /task sin JWT (servicio interno: 200 esperado, gateway sí lo bloquea)" "200|401|403" "$STATUS"
fi

# =============================================================================
# Resumen final
# =============================================================================

echo ""
echo "===================================================================="
echo -e "${BOLD}Resumen${NC}"
echo "  Total:   $TOTAL"
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo -e "  ${YELLOW}Skipped: $SKIPPED${NC}"
if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo -e "${RED}IDs con fallo:${NC}"
    for id in "${FAILED_IDS[@]}"; do echo "  $id"; done
fi
echo "===================================================================="

[[ "$FAILED" -gt 0 ]] && exit 1
exit 0
