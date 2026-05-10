#!/usr/bin/env bash
# =============================================================================
# season-service — script QA end-to-end del plan test-suite-plan-season-service.md
# =============================================================================
# Ejecuta los casos del plan que son verificables desde fuera del proceso
# (HTTP, gRPC dependencies y Kafka cascade). Los unit / WebMvc slice / JDBC
# tests internos del plan no se ejecutan aquí: viven en src/test y se corren
# con `./mvnw -pl season-service test`. Este script es para que un QA o
# desarrollador valide el comportamiento real del servicio corriendo
# end-to-end con sus dependencias.
#
# REQUISITOS PARA UNA EJECUCIÓN COMPLETA:
#   - season-service corriendo y accesible en $API_URL (default :8082).
#   - terrain-service corriendo en :9093 (gRPC) con al menos un terreno
#     cuyo UUID sea $TERRAIN_ID (verificable con CheckTerrainExists).
#   - crop-service corriendo en :9094 (gRPC) con al menos un cultivo
#     cuyo UUID sea $CROP_ID.
#   - api-gateway corriendo en $GATEWAY_URL si quieres ejecutar §11 (auth).
#   - Broker Kafka accesible en $KAFKA_BOOTSTRAP para §6 (cascada).
#   - Herramientas: curl, jq (obligatorias). kcat (§6), grpcurl (§5).
#
# USO:
#   ./test-season-plan.sh                                  # corre todo lo que pueda
#   API_URL=http://localhost:8082 ./test-season-plan.sh
#   TERRAIN_ID=<uuid> CROP_ID=<uuid> ./test-season-plan.sh
#   ONLY="1 2 4" ./test-season-plan.sh                     # solo §1, §2 y §4
#   SKIP_SLOW=1 ./test-season-plan.sh                      # salta tests >1 MB
#
# SALIDA:
#   [PASS] SEASON-X.YY - descripción
#   [FAIL] SEASON-X.YY - expected ... but got ...
#   [SKIP] SEASON-X.YY - razón
#   ...
#   Resumen final + exit code 0 si todos los assertions pasaron.
# =============================================================================

set -u
set -o pipefail

# ---------- Configuración (overridable por env) -----------------------------
API_URL="${API_URL:-http://localhost:8084}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9000}"
GRPC_TERRAIN="${GRPC_TERRAIN:-localhost:9093}"
GRPC_CROP="${GRPC_CROP:-localhost:9094}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

# IDs de fixtures (necesitan existir en sus servicios respectivos)
TERRAIN_ID="dde73354-b39d-4750-ac37-70605e9fdf15"
TERRAIN_ID_GHOST="${TERRAIN_ID_GHOST:-00000000-0000-0000-0000-000000000000}"
CROP_ID="4c04e734-5134-49bc-a622-6ae30e8920fc"
CROP_ID_GHOST="${CROP_ID_GHOST:-00000000-0000-0000-0000-000000000001}"

RUN_TAG="QA-SEASON-$(date +%s)"

SKIP_SLOW="${SKIP_SLOW:-0}"
ONLY="${ONLY:-}"

# ---------- Colores ---------------------------------------------------------
if [[ -t 1 ]]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'
    BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; BLUE=''; BOLD=''; NC=''
fi

# ---------- Contadores -----------------------------------------------------
TOTAL=0; PASSED=0; FAILED=0; SKIPPED=0
declare -a FAILED_IDS=()

# ---------- Tracking de recursos creados ------------------------------------
declare -a CREATED_SEASON_IDS=()
TMP_DIR="$(mktemp -d -t season-qa-XXXXXX)"
trap 'cleanup_resources; rm -rf "$TMP_DIR"' EXIT

# ---------- Helpers de detección de herramientas ---------------------------
HAS_JQ=0; HAS_CURL=0; HAS_GRPCURL=0; HAS_KCAT=0
command -v jq >/dev/null 2>&1 && HAS_JQ=1
command -v curl >/dev/null 2>&1 && HAS_CURL=1
command -v grpcurl >/dev/null 2>&1 && HAS_GRPCURL=1
command -v kcat >/dev/null 2>&1 && HAS_KCAT=1
command -v kafkacat >/dev/null 2>&1 && HAS_KCAT=1

if [[ "$HAS_JQ" -eq 0 || "$HAS_CURL" -eq 0 ]]; then
    echo -e "${RED}[FATAL]${NC} jq y curl son obligatorios. Instálalos antes de seguir."
    exit 2
fi

# ---------- Helpers de aserciones -------------------------------------------

_pass() {
    local id="$1" desc="$2"
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
    echo -e "${GREEN}[PASS]${NC} ${id} - ${desc}"
}

_fail() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
    FAILED_IDS+=("$id")
    echo -e "${RED}[FAIL]${NC} ${id} - ${desc}"
    echo -e "        ${YELLOW}expected:${NC} ${expected}"
    echo -e "        ${YELLOW}actual:${NC}   ${actual}"
}

skip_test() {
    local id="$1" reason="$2"
    SKIPPED=$((SKIPPED + 1))
    TOTAL=$((TOTAL + 1))
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

assert_json_length() {
    local id="$1" desc="$2" expected="$3" body="$4"
    local actual
    actual=$(echo "$body" | jq 'length' 2>/dev/null || echo "<jq-error>")
    if [[ "$actual" == "$expected" ]]; then
        _pass "$id" "$desc (length = $expected)"
    else
        _fail "$id" "$desc" "length = $expected" "length = $actual"
    fi
}

section_enabled() {
    local n="$1"
    if [[ -z "$ONLY" ]]; then return 0; fi
    [[ " $ONLY " == *" $n "* ]]
}

section_header() {
    local n="$1" title="$2"
    echo
    echo -e "${BOLD}${BLUE}=== Sección $n: $title ===${NC}"
}

# Helper para construir bodies de SeasonRequest
make_body() {
    local terrain="$1" crop="$2" start="${3:-2025-03-01}" end="${4:-}" type="${5:-}" obs="${6:-}"
    local body="{\"terrain_id\":\"$terrain\",\"crop_id\":\"$crop\",\"start_date\":\"$start\""
    [[ -n "$end" ]] && body+=",\"end_date\":\"$end\""
    [[ -n "$type" ]] && body+=",\"season_type_id\":$type"
    [[ -n "$obs" ]] && body+=",\"observations\":\"$obs\""
    body+="}"
    echo "$body"
}

# Crea una season y registra su id para cleanup
create_season() {
    local body="$1"
    local resp
    resp=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" -d "$body")
    local body_only="${resp%$'\n'*}"
    local status="${resp##*$'\n'}"
    if [[ "$status" == "201" ]]; then
        local id
        id=$(echo "$body_only" | jq -r '.')
        if [[ -n "$id" && "$id" != "null" ]]; then
            CREATED_SEASON_IDS+=("$id")
            echo "$id"
            return 0
        fi
    fi
    return 1
}

cleanup_resources() {
    echo
    echo -e "${BLUE}=== Cleanup ===${NC}"
    if [[ ${#CREATED_SEASON_IDS[@]} -eq 0 ]]; then
        echo "  (no hay seasons para limpiar)"
        return 0
    fi
    for sid in "${CREATED_SEASON_IDS[@]}"; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/$sid")
        echo "  DELETE $sid → $code"
    done
}

# ---------- Pre-flight ------------------------------------------------------
echo -e "${BOLD}${BLUE}=== Pre-flight ===${NC}"
PING_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/00000000-0000-0000-0000-000000000000" || echo "000")
if [[ "$PING_STATUS" == "000" ]]; then
    echo -e "${RED}[FATAL]${NC} season-service NO responde en $API_URL. Arráncalo y reintenta."
    exit 2
fi
echo "  season-service en $API_URL responde (status=$PING_STATUS, esperable 404 o 4xx). OK."
[[ "$HAS_GRPCURL" -eq 1 ]] && echo "  grpcurl disponible — §5 (smoke contracts) ON" || echo "  grpcurl NO — §5 SKIP"
[[ "$HAS_KCAT" -eq 1 ]] && echo "  kcat disponible — §6 (cascada) ON" || echo "  kcat NO — §6 SKIP"

# ---------- Detección de fixtures y dependencias gRPC -----------------------
# Muchos casos del plan necesitan TERRAIN_ID + CROP_ID que existan en sus
# servicios respectivos. Si no existen (o si los gRPC están caídos), el POST
# falla con 500 — y todos los tests dependientes fallan en cadena. Aquí
# detectamos la situación una sola vez y los marcamos como SKIP con razón.

GRPC_DEPS_OK=0          # 1 si terrain-service y crop-service responden gRPC
TERRAIN_FIXTURE_OK=0    # 1 si TERRAIN_ID existe en terrain-service
CROP_FIXTURE_OK=0       # 1 si CROP_ID existe en crop-service

# Verificación real con grpcurl si está disponible
if [[ "$HAS_GRPCURL" -eq 1 ]]; then
    OUT=$(grpcurl -plaintext -d "{\"terrain_id\":\"$TERRAIN_ID\"}" \
        "$GRPC_TERRAIN" com.agro.terrain.grpc.TerrainService/CheckTerrainExists 2>&1 || echo "ERROR")
    if echo "$OUT" | grep -q '"exists": *true'; then
        GRPC_DEPS_OK=1
        TERRAIN_FIXTURE_OK=1
    elif echo "$OUT" | grep -q '"exists": *false'; then
        GRPC_DEPS_OK=1   # gRPC vivo, pero el fixture no existe
    fi
    OUT=$(grpcurl -plaintext -d "{\"crop_id\":\"$CROP_ID\"}" \
        "$GRPC_CROP" com.agro.crop.grpc.CropService/CheckCropExists 2>&1 || echo "ERROR")
    if echo "$OUT" | grep -q '"exists": *true'; then
        CROP_FIXTURE_OK=1
    fi
fi

# Si no hay grpcurl, hacemos un sondeo via POST: una respuesta 500 indica gRPC
# muerto; 404 / 201 indican gRPC vivo (con fixtures válidos o no).
if [[ "$HAS_GRPCURL" -eq 0 ]]; then
    SMOKE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID")")
    case "$SMOKE" in
        201) GRPC_DEPS_OK=1; TERRAIN_FIXTURE_OK=1; CROP_FIXTURE_OK=1 ;;
        404) GRPC_DEPS_OK=1 ;;  # uno de los fixtures no existe; al menos los servicios responden
        500) GRPC_DEPS_OK=0 ;;  # gRPC caído
    esac
fi

if [[ "$GRPC_DEPS_OK" -eq 0 ]]; then
    echo -e "${YELLOW}[WARN]${NC} terrain-service ($GRPC_TERRAIN) o crop-service ($GRPC_CROP) NO responden gRPC."
    echo "  Tests que requieren creación de seasons reales se marcarán como [SKIP]."
    echo "  Para correr todo: docker compose up -d terrain-service crop-service"
elif [[ "$TERRAIN_FIXTURE_OK" -eq 0 || "$CROP_FIXTURE_OK" -eq 0 ]]; then
    echo -e "${YELLOW}[WARN]${NC} TERRAIN_ID o CROP_ID no existen en sus servicios."
    echo "  TERRAIN_ID=$TERRAIN_ID  exists=$TERRAIN_FIXTURE_OK"
    echo "  CROP_ID=$CROP_ID    exists=$CROP_FIXTURE_OK"
    echo "  Override con TERRAIN_ID=<uuid-real> CROP_ID=<uuid-real> ./test-season-plan.sh"
    echo "  Tests que requieren INSERTar una season real se marcarán como [SKIP]."
else
    echo "  TERRAIN_ID y CROP_ID validados — todos los §1/§2/§4/§10 dependientes ON."
fi

# Guard helper: usar antes de tests que requieren INSERTar una season real.
require_real_fixtures() {
    [[ "$GRPC_DEPS_OK" -eq 1 && "$TERRAIN_FIXTURE_OK" -eq 1 && "$CROP_FIXTURE_OK" -eq 1 ]]
}

# ===========================================================================
# §1. POST /season
# ===========================================================================
if section_enabled 1; then
section_header 1 "POST /season — alta de season"

# SEASON-1.01 happy path mínimo
if require_real_fixtures; then
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID")")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-1.01" "happy path mínimo" "201" "$STATUS"
    if [[ "$STATUS" == "201" ]]; then
        SID=$(echo "$BODY" | jq -r '.')
        [[ -n "$SID" && "$SID" != "null" ]] && CREATED_SEASON_IDS+=("$SID")
    fi
else
    skip_test "SEASON-1.01" "requiere TERRAIN_ID y CROP_ID reales (gRPC deps OK)"
fi

# SEASON-1.02 happy path completo
if require_real_fixtures; then
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-03-01 2025-08-01 1 "test-1.02")")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-1.02" "happy path completo" "201" "$STATUS"
    [[ "$STATUS" == "201" ]] && CREATED_SEASON_IDS+=("$(echo "$BODY" | jq -r '.')")
else
    skip_test "SEASON-1.02" "requiere fixtures reales"
fi

# SEASON-1.04 terrain_id ausente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "{\"crop_id\":\"$CROP_ID\",\"start_date\":\"2025-03-01\"}")
assert_status "SEASON-1.04" "terrain_id ausente → 400" "400" "$STATUS"

# SEASON-1.05 terrain_id no UUID
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "{\"terrain_id\":\"abc\",\"crop_id\":\"$CROP_ID\",\"start_date\":\"2025-03-01\"}")
assert_status "SEASON-1.05" "terrain_id no UUID → 400" "400" "$STATUS"

# SEASON-1.06 crop_id ausente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "{\"terrain_id\":\"$TERRAIN_ID\",\"start_date\":\"2025-03-01\"}")
assert_status "SEASON-1.06" "crop_id ausente → 400" "400" "$STATUS"

# SEASON-1.08 start_date ausente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "{\"terrain_id\":\"$TERRAIN_ID\",\"crop_id\":\"$CROP_ID\"}")
assert_status "SEASON-1.08" "start_date ausente → 400" "400" "$STATUS"

# SEASON-1.09 start_date formato incorrecto
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "{\"terrain_id\":\"$TERRAIN_ID\",\"crop_id\":\"$CROP_ID\",\"start_date\":\"01/01/2025\"}")
assert_status "SEASON-1.09" "start_date formato incorrecto → 400" "400" "$STATUS"

# SEASON-1.10 end_date < start_date
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-08-01 2025-03-01)")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "SEASON-1.10" "end_date < start_date → 400" "400" "$STATUS"
assert_json_contains "SEASON-1.10b" "errors menciona dates.range" '.errors // [] | tostring' "fecha de fin" "$BODY"

# SEASON-1.11 end_date == start_date
if require_real_fixtures; then
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-03-01 2025-03-01)")
    STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-1.11" "end_date == start_date → 201 (borde válido)" "201" "$STATUS"
    [[ "$STATUS" == "201" ]] && CREATED_SEASON_IDS+=("$(echo "${RESP%$'\n'*}" | jq -r '.')")
else
    skip_test "SEASON-1.11" "requiere fixtures reales"
fi

# SEASON-1.13 observations > 2000 chars
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "SEASON-1.13" "SKIP_SLOW=1"
else
    BIG=$(printf 'X%.0s' $(seq 1 2001))
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "{\"terrain_id\":\"$TERRAIN_ID\",\"crop_id\":\"$CROP_ID\",\"start_date\":\"2025-03-01\",\"observations\":\"$BIG\"}")
    assert_status "SEASON-1.13" "observations > 2000 chars → 400" "400" "$STATUS"
fi

# SEASON-1.19 terrain_id inexistente: solo necesita gRPC arriba (no fixtures válidos)
if [[ "$GRPC_DEPS_OK" -eq 1 ]]; then
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID_GHOST" "$CROP_ID")")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-1.19" "terrain_id inexistente → 404" "404" "$STATUS"
    assert_json_eq "SEASON-1.19b" "title 'Terrain not found'" '.title' "Terrain not found" "$BODY"
else
    skip_test "SEASON-1.19" "terrain-service no responde gRPC"
    skip_test "SEASON-1.19b" "terrain-service no responde gRPC"
fi

# SEASON-1.20 crop_id inexistente: necesita gRPC arriba + TERRAIN_ID válido
# (porque el orden es terrain primero; si el terrain falla, nunca llegamos al check de crop)
if [[ "$GRPC_DEPS_OK" -eq 1 && "$TERRAIN_FIXTURE_OK" -eq 1 ]]; then
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID_GHOST")")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-1.20" "crop_id inexistente → 404" "404" "$STATUS"
    assert_json_eq "SEASON-1.20b" "title 'Crop not found'" '.title' "Crop not found" "$BODY"
else
    skip_test "SEASON-1.20" "requiere TERRAIN_ID válido + crop-service vivo"
    skip_test "SEASON-1.20b" "requiere TERRAIN_ID válido + crop-service vivo"
fi

# SEASON-1.24 body vacío
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" -d "{}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "SEASON-1.24" "body vacío → 400" "400" "$STATUS"
ERR_LEN=$(echo "$BODY" | jq -r '.errors | length' 2>/dev/null || echo "0")
if [[ "$ERR_LEN" -ge "3" ]]; then
    _pass "SEASON-1.24b" "errors contiene ≥ 3 entradas (got $ERR_LEN)"
else
    _fail "SEASON-1.24b" "errors con ≥ 3" "≥ 3" "$ERR_LEN"
fi

# SEASON-1.25 Content-Type text/plain
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: text/plain" -d "no json")
assert_status_in "SEASON-1.25" "Content-Type text/plain → 415" "415" "$STATUS"

# SEASON-1.26 JSON malformado
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
    -H "Content-Type: application/json" -d "{\"terrain_id\":")
assert_status "SEASON-1.26" "JSON malformado → 400" "400" "$STATUS"

# SEASON-1.27 idempotencia (no UNIQUE)
if require_real_fixtures; then
    S1=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-04-01 "" "" "dup")")
    S2=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-04-01 "" "" "dup")")
    ID1=$(echo "$S1" | jq -r '.' 2>/dev/null)
    ID2=$(echo "$S2" | jq -r '.' 2>/dev/null)
    [[ -n "$ID1" && "$ID1" != "null" ]] && CREATED_SEASON_IDS+=("$ID1")
    [[ -n "$ID2" && "$ID2" != "null" ]] && CREATED_SEASON_IDS+=("$ID2")
    if [[ -n "$ID1" && -n "$ID2" && "$ID1" != "$ID2" ]]; then
        _pass "SEASON-1.27" "dos POST iguales → 2 UUIDs distintos ($ID1 ≠ $ID2)"
    else
        _fail "SEASON-1.27" "dos POST iguales generan 2 filas" "ids distintos" "id1=$ID1 id2=$ID2"
    fi
else
    skip_test "SEASON-1.27" "requiere fixtures reales"
fi

fi  # /sección 1

# ===========================================================================
# §2. GET /season/{id}
# ===========================================================================
if section_enabled 2; then
section_header 2 "GET /season/{id} — detalle"

# Pre-seed: tomar uno de los creados o crear uno nuevo (requiere fixtures reales)
SID=""
if [[ ${#CREATED_SEASON_IDS[@]} -gt 0 ]]; then
    SID="${CREATED_SEASON_IDS[0]}"
elif require_real_fixtures; then
    SID=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID")" | jq -r '.' 2>/dev/null)
    [[ -n "$SID" && "$SID" != "null" ]] && CREATED_SEASON_IDS+=("$SID")
fi

if [[ -n "$SID" && "$SID" != "null" ]]; then
    # SEASON-2.01 detalle existente
    RESP=$(curl -s -w "\n%{http_code}" "$API_URL/season/$SID")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "SEASON-2.01" "detalle existente → 200" "200" "$STATUS"
    assert_json_eq "SEASON-2.01b" "id devuelto coincide" '.id' "$SID" "$BODY"

    # SEASON-2.03 fields=id,start_date
    RESP=$(curl -s "$API_URL/season/$SID?fields=id,start_date")
    KEYS=$(echo "$RESP" | jq -r 'keys | sort | join(",")')
    if [[ "$KEYS" == "id,start_date" ]]; then
        _pass "SEASON-2.03" "fields proyecta solo id,start_date"
    else
        _fail "SEASON-2.03" "proyección dinámica" "id,start_date" "$KEYS"
    fi

    # SEASON-2.04 fields fuera del enum
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/$SID?fields=secret")
    assert_status "SEASON-2.04" "fields=secret → 400" "400" "$STATUS"
else
    skip_test "SEASON-2.01" "no hay season real para inspeccionar (fixtures)"
    skip_test "SEASON-2.01b" "no hay season real"
    skip_test "SEASON-2.03" "no hay season real"
    skip_test "SEASON-2.04" "no hay season real"
fi

# SEASON-2.02 detalle inexistente
RANDOM_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/season/$RANDOM_UUID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "SEASON-2.02" "detalle inexistente → 404" "404" "$STATUS"
assert_json_eq "SEASON-2.02b" "title 'Resource not found'" '.title' "Resource not found" "$BODY"

# SEASON-2.09 id no UUID
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/abc")
assert_status "SEASON-2.09" "id no UUID → 400" "400" "$STATUS"

fi  # /sección 2

# ===========================================================================
# §3. GET /season/terrain/{terrainId}
# ===========================================================================
if section_enabled 3; then
section_header 3 "GET /season/terrain/{terrainId} — listar por terreno"

# SEASON-3.01 terreno con seasons
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/season/terrain/$TERRAIN_ID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "SEASON-3.01" "GET por terreno → 200" "200" "$STATUS"
LEN=$(echo "$BODY" | jq 'length')
if [[ "$LEN" -ge 1 ]]; then
    _pass "SEASON-3.01b" "lista contiene ≥ 1 seasons (count=$LEN)"
else
    _fail "SEASON-3.01b" "lista con ≥ 1" "≥ 1" "$LEN"
fi

# SEASON-3.02 terreno sin seasons
RANDOM_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
RESP=$(curl -s "$API_URL/season/terrain/$RANDOM_UUID")
LEN=$(echo "$RESP" | jq 'length')
if [[ "$LEN" == "0" ]]; then
    _pass "SEASON-3.02" "terreno sin seasons → []"
else
    _fail "SEASON-3.02" "lista vacía" "[]" "length=$LEN"
fi

# SEASON-3.03 orden DESC
RESP=$(curl -s "$API_URL/season/terrain/$TERRAIN_ID?fields=id,start_date")
DATES=$(echo "$RESP" | jq -r '[.[].start_date] | join(",")')
SORTED=$(echo "$RESP" | jq -r '[.[].start_date] | sort | reverse | join(",")')
if [[ "$DATES" == "$SORTED" ]]; then
    _pass "SEASON-3.03" "orden por start_date DESC respetado"
else
    _fail "SEASON-3.03" "orden DESC" "$SORTED" "$DATES"
fi

# SEASON-3.05 terrainId no UUID
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/terrain/abc")
assert_status "SEASON-3.05" "terrainId no UUID → 400" "400" "$STATUS"

fi  # /sección 3

# ===========================================================================
# §4. DELETE /season/{id}
# ===========================================================================
if section_enabled 4; then
section_header 4 "DELETE /season/{id} — borrado manual"

# SEASON-4.01 happy path: requiere crear una season real para luego borrarla
if require_real_fixtures; then
    TARGET_BODY=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-05-01)")
    TARGET=$(echo "$TARGET_BODY" | jq -r '.' 2>/dev/null)
    if [[ -n "$TARGET" && "$TARGET" != "null" ]]; then
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/$TARGET")
        assert_status "SEASON-4.01" "DELETE happy path → 204" "204" "$STATUS"
        AFTER=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/$TARGET")
        assert_status "SEASON-4.01b" "fila desapareció (GET → 404)" "404" "$AFTER"
    else
        _fail "SEASON-4.01" "no se pudo crear el target del DELETE" "201" "$TARGET_BODY"
    fi
else
    skip_test "SEASON-4.01" "requiere fixtures reales para crear el target"
    skip_test "SEASON-4.01b" "requiere fixtures reales"
fi

# SEASON-4.02 id inexistente
RANDOM_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
RESP=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/season/$RANDOM_UUID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "SEASON-4.02" "id inexistente → 404" "404" "$STATUS"
assert_json_eq "SEASON-4.02b" "title 'Resource not found'" '.title' "Resource not found" "$BODY"

# SEASON-4.03 id no UUID
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/abc")
assert_status "SEASON-4.03" "id no UUID → 400" "400" "$STATUS"

# SEASON-4.04 doble DELETE
if require_real_fixtures; then
    TARGET_BODY=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID" 2025-05-15)")
    TARGET=$(echo "$TARGET_BODY" | jq -r '.' 2>/dev/null)
    if [[ -n "$TARGET" && "$TARGET" != "null" ]]; then
        S1=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/$TARGET")
        S2=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/$TARGET")
        if [[ "$S1" == "204" && "$S2" == "404" ]]; then
            _pass "SEASON-4.04" "doble DELETE → 204 + 404"
        else
            _fail "SEASON-4.04" "doble DELETE → 204+404" "204+404" "$S1+$S2"
        fi
    else
        _fail "SEASON-4.04" "no se pudo crear el target" "201" "$TARGET_BODY"
    fi
else
    skip_test "SEASON-4.04" "requiere fixtures reales"
fi

fi  # /sección 4

# ===========================================================================
# §5. gRPC (smoke contract)
# ===========================================================================
if section_enabled 5; then
section_header 5 "gRPC (smoke — verificación de que terrain-service y crop-service están vivos)"

if [[ "$HAS_GRPCURL" -eq 0 ]]; then
    skip_test "SEASON-5.*" "grpcurl no instalado"
else
    # SEASON-5.A smoke terrain
    OUT=$(grpcurl -plaintext -d "{\"terrain_id\":\"$TERRAIN_ID\"}" \
        "$GRPC_TERRAIN" com.agro.terrain.grpc.TerrainService/CheckTerrainExists 2>&1 || echo "ERROR")
    if echo "$OUT" | grep -q '"exists": *true'; then
        _pass "SEASON-5.A" "terrain-service responde exists=true para TERRAIN_ID"
    else
        _fail "SEASON-5.A" "terrain-service vivo" '"exists": true' "$OUT"
    fi

    # SEASON-5.B smoke crop
    OUT=$(grpcurl -plaintext -d "{\"crop_id\":\"$CROP_ID\"}" \
        "$GRPC_CROP" com.agro.crop.grpc.CropService/CheckCropExists 2>&1 || echo "ERROR")
    if echo "$OUT" | grep -q '"exists": *true'; then
        _pass "SEASON-5.B" "crop-service responde exists=true para CROP_ID"
    else
        _fail "SEASON-5.B" "crop-service vivo" '"exists": true' "$OUT"
    fi
fi

fi  # /sección 5

# ===========================================================================
# §6. Cascada Kafka terrain-deleted
# ===========================================================================
if section_enabled 6; then
section_header 6 "Cascada Kafka — terrain-deleted borra seasons"

if [[ "$HAS_KCAT" -eq 0 ]]; then
    skip_test "SEASON-6.*" "kcat no instalado"
else
    # Generar un terreno "fake" (UUID aleatorio que el test publica como
    # borrado). Crear N seasons sobre él SOLO funciona si el gRPC también
    # acepta este terreno. Para el test usamos $TERRAIN_ID y luego
    # verificamos que su listado encoge.
    BEFORE_LEN=$(curl -s "$API_URL/season/terrain/$TERRAIN_ID" | jq 'length')

    # Publicar terrain-deleted con __TypeId__ del productor
    echo "{\"terrainId\":\"$TERRAIN_ID\"}" | \
        kcat -b "$KAFKA_BOOTSTRAP" -t terrain-deleted -P -q \
             -H "__TypeId__=com.agro.terrainservice.event.TerrainDeletedEvent" 2>&1 | head -3
    sleep 2

    AFTER_LEN=$(curl -s "$API_URL/season/terrain/$TERRAIN_ID" | jq 'length')
    if [[ "$AFTER_LEN" == "0" ]]; then
        _pass "SEASON-6.10" "tras publicar terrain-deleted, las seasons del terrain ($BEFORE_LEN → 0) se borraron"
    else
        _fail "SEASON-6.10" "cascada Kafka borra todas las seasons" "0" "length=$AFTER_LEN"
    fi
fi

fi  # /sección 6

# ===========================================================================
# §10. Seguridad
# ===========================================================================
if section_enabled 10; then
section_header 10 "Seguridad defensiva"

# SEASON-10.01 SQL injection en fields (necesita una season real para hacer GET)
if require_real_fixtures; then
    TARGET=$(curl -s -X POST "$API_URL/season" -H "Content-Type: application/json" \
        -d "$(make_body "$TERRAIN_ID" "$CROP_ID")" | jq -r '.' 2>/dev/null)
    if [[ -n "$TARGET" && "$TARGET" != "null" ]]; then
        CREATED_SEASON_IDS+=("$TARGET")
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            "$API_URL/season/$TARGET?fields=id;DROP%20TABLE%20season;--")
        assert_status "SEASON-10.01" "SQL injection fields → 400" "400" "$STATUS"
        PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/season/$TARGET")
        assert_status "SEASON-10.01b" "tabla intacta tras intento" "200" "$PING"
    else
        skip_test "SEASON-10.01" "no se pudo crear el target"
        skip_test "SEASON-10.01b" "no se pudo crear el target"
    fi
else
    skip_test "SEASON-10.01" "requiere fixtures reales"
    skip_test "SEASON-10.01b" "requiere fixtures reales"
fi

# SEASON-10.03 propiedades extra (Jackson ignora)
if require_real_fixtures; then
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/season" \
        -H "Content-Type: application/json" \
        -d "{\"terrain_id\":\"$TERRAIN_ID\",\"crop_id\":\"$CROP_ID\",\"start_date\":\"2025-03-01\",\"isAdmin\":true}")
    assert_status "SEASON-10.03" "body con isAdmin extra → 201 (Jackson ignora)" "201" "$STATUS"
else
    skip_test "SEASON-10.03" "requiere fixtures reales"
fi

# SEASON-10.06 path traversal
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/season/../../etc/passwd")
assert_status_in "SEASON-10.06" "path traversal → 400/404" "400|404" "$STATUS"

fi  # /sección 10

# ===========================================================================
# Resumen final
# ===========================================================================
echo
echo -e "${BOLD}${BLUE}=== Resumen ===${NC}"
echo -e "  Total:    $TOTAL"
echo -e "  ${GREEN}Passed:${NC}   $PASSED"
echo -e "  ${RED}Failed:${NC}   $FAILED"
echo -e "  ${YELLOW}Skipped:${NC}  $SKIPPED"
if [[ "$FAILED" -gt 0 ]]; then
    echo
    echo -e "  ${RED}IDs fallidos:${NC}"
    for id in "${FAILED_IDS[@]}"; do echo "    - $id"; done
    exit 1
fi
exit 0
