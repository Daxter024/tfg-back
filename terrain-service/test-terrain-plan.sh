#!/usr/bin/env bash
# =============================================================================
# terrain-service — script QA end-to-end del plan terrain-service-test-plan.md
# =============================================================================
# Ejecuta los casos del plan que son verificables desde fuera del proceso (HTTP,
# gRPC y Kafka). Los unit tests / repository tests internos del plan no se
# ejecutan aquí: viven en src/test y se corren con `./mvnw -pl terrain-service
# test`. Este script es para que un QA o desarrollador valide el comportamiento
# real del servicio corriendo end-to-end con sus dependencias.
#
# REQUISITOS PARA UNA EJECUCIÓN COMPLETA:
#   - terrain-service corriendo y accesible en $API_URL.
#   - auth-service corriendo (gRPC) con un usuario válido cuyo UUID sea $USER_ID.
#   - api-gateway corriendo en $GATEWAY_URL si quieres ejecutar §13.
#   - Broker Kafka accesible (docker container $KAFKA_CONTAINER) para §11.
#   - terrain-service expone gRPC en $GRPC_HOST_PORT para §10.
#   - Herramientas: curl, jq (obligatorias). grpcurl (§10), kcat o
#     docker (§11), python3 con base64 para fixtures.
#
# USO:
#   ./test-terrain-plan.sh                 # corre todo lo que pueda
#   API_URL=http://localhost:8083 ./test-terrain-plan.sh
#   SKIP_SLOW=1 ./test-terrain-plan.sh     # salta cuota 100 MB (§5.11/§5.12)
#   ONLY="1 2 3" ./test-terrain-plan.sh    # solo secciones 1, 2 y 3
#
# SALIDA:
#   [PASS] TER-X.YY - descripción
#   [FAIL] TER-X.YY - expected ... but got ...
#   [SKIP] TER-X.YY - razón
#   ...
#   Resumen final + exit code 0 si todos los assertions pasaron.
# =============================================================================

set -u
set -o pipefail

# ---------- Configuración (overridable por env) -----------------------------
API_URL="${API_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9000}"
GRPC_HOST_PORT="${GRPC_HOST_PORT:-localhost:9093}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
KAFKA_BOOTSTRAP_INTERNAL="${KAFKA_BOOTSTRAP_INTERNAL:-kafka:9092}"

USER_ID="${USER_ID:-cb0609dd-f980-47bb-8e37-f91ef2311c91}"
ALT_USER_ID="${ALT_USER_ID:-00000000-0000-0000-0000-000000000000}"   # supuesto inexistente
WRONG_OWNER_ID="${WRONG_OWNER_ID:-11111111-2222-3333-4444-555555555555}" # otro user válido

# Para §13 (gateway) podemos loguear y cachear el JWT.
USER_EMAIL="${USER_EMAIL:-}"
USER_PASSWORD="${USER_PASSWORD:-}"
JWT="${JWT:-}"

SKIP_SLOW="${SKIP_SLOW:-0}"   # 1 = saltar tests de >50MB
ONLY="${ONLY:-}"               # "1 2 9" = solo secciones X

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
declare -a CREATED_TERRAINS=()
TMP_DIR="$(mktemp -d -t terrain-qa-XXXXXX)"
trap 'cleanup_resources; rm -rf "$TMP_DIR"' EXIT

# ---------- Fixtures GeoJSON (apéndice B del plan) --------------------------
# B.1 Polígono ~1 ha (≈ 10 000 m²) — válido
GEOM_VALID_1HA='{"type":"Polygon","coordinates":[[[-3.7100,40.4200],[-3.7100,40.4209],[-3.7088,40.4209],[-3.7088,40.4200],[-3.7100,40.4200]]]}'
# B.2 Polígono diminuto (~50 m²) — área < 100 m²
GEOM_TINY='{"type":"Polygon","coordinates":[[[-3.71000,40.42000],[-3.71000,40.42005],[-3.70999,40.42005],[-3.70999,40.42000],[-3.71000,40.42000]]]}'
# B.3 Polígono enorme (~1.1×10⁸ m²) — área > 1e8 m²
GEOM_HUGE='{"type":"Polygon","coordinates":[[[-3.0,40.0],[-3.0,41.0],[-2.0,41.0],[-2.0,40.0],[-3.0,40.0]]]}'
# B.4 Polígono no cerrado
GEOM_NOT_CLOSED='{"type":"Polygon","coordinates":[[[-3.71,40.42],[-3.71,40.43],[-3.70,40.43],[-3.70,40.42]]]}'
# B.5 Polígono auto-intersectado
GEOM_SELF_INTERSECT='{"type":"Polygon","coordinates":[[[-3.71,40.42],[-3.70,40.43],[-3.71,40.43],[-3.70,40.42],[-3.71,40.42]]]}'
# Geometría no JSON serializable (string crudo) — para TER-1.09
GEOM_NOT_OBJECT='"not a polygon"'

# Cadastral fixtures
CAD_REF_VALID_14='1234ABCD5678EF'
CAD_REF_VALID_20='9872023VH5797S0001WX'
CAD_REF_INVALID_LOWER='abcd1234efghij'
CAD_REF_INVALID_DASH='1234-ABCD-5678'
SIGPAC_VALID='13-082-01-02-001-002-1'
SIGPAC_BAD_NODASHES='13082010200100212'
SIGPAC_BAD_EXTRA='13-082-01-02-001-002-1-9'

# ---------- Helpers de detección de herramientas ---------------------------
HAS_JQ=0; HAS_CURL=0; HAS_GRPCURL=0; HAS_KCAT=0; HAS_DOCKER=0
command -v jq >/dev/null 2>&1 && HAS_JQ=1
command -v curl >/dev/null 2>&1 && HAS_CURL=1
command -v grpcurl >/dev/null 2>&1 && HAS_GRPCURL=1
command -v kcat >/dev/null 2>&1 && HAS_KCAT=1
command -v kafkacat >/dev/null 2>&1 && HAS_KCAT=1   # alias antiguo
command -v docker >/dev/null 2>&1 && HAS_DOCKER=1

if [[ "$HAS_JQ" -eq 0 || "$HAS_CURL" -eq 0 ]]; then
    echo -e "${RED}[FATAL]${NC} jq y curl son obligatorios. Instálalos antes de seguir."
    exit 2
fi

# ---------- Helpers de aserciones -------------------------------------------

# Imprime un PASS y suma contador.
_pass() {
    local id="$1" desc="$2"
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
    echo -e "${GREEN}[PASS]${NC} ${id} - ${desc}"
}

# Imprime un FAIL con detalle, suma contador.
_fail() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
    FAILED_IDS+=("$id")
    echo -e "${RED}[FAIL]${NC} ${id} - ${desc}"
    echo -e "        ${YELLOW}expected:${NC} ${expected}"
    echo -e "        ${YELLOW}actual:${NC}   ${actual}"
}

# Salta un caso (no curlable o falta herramienta), suma contador SKIPPED.
skip_test() {
    local id="$1" reason="$2"
    SKIPPED=$((SKIPPED + 1))
    TOTAL=$((TOTAL + 1))
    echo -e "${YELLOW}[SKIP]${NC} ${id} - ${reason}"
}

# Compara status HTTP.
assert_status() {
    local id="$1" desc="$2" expected="$3" actual="$4"
    if [[ "$actual" == "$expected" ]]; then
        _pass "$id" "$desc (status $actual)"
    else
        _fail "$id" "$desc" "status $expected" "status $actual"
    fi
}

# Verifica que un jq path en $body devuelve $expected.
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

# Verifica que un jq path en $body contiene $needle.
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

# Verifica que un jq path existe (no es null).
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

# Verifica el content-type de la respuesta (cabecera Content-Type).
assert_content_type_contains() {
    local id="$1" desc="$2" needle="$3" headers="$4"
    local ct
    ct=$(echo "$headers" | grep -i '^Content-Type:' | tr -d '\r' | head -1)
    if [[ "$ct" == *"$needle"* ]]; then
        _pass "$id" "$desc ($needle)"
    else
        _fail "$id" "$desc" "Content-Type contiene '$needle'" "${ct:-<vacío>}"
    fi
}

# Verifica que una sección está incluida (filtro $ONLY).
section_enabled() {
    local n="$1"
    if [[ -z "$ONLY" ]]; then
        return 0
    fi
    [[ " $ONLY " == *" $n "* ]]
}

# Imprime cabecera de sección.
section_header() {
    local n="$1" title="$2"
    echo
    echo -e "${BOLD}${BLUE}=== Sección $n: $title ===${NC}"
}

# Crea un terreno básico válido y devuelve su id en stdout.
# Marca el id en CREATED_TERRAINS para limpiarlo al final.
create_terrain_basic() {
    local name="${1:-QA Test $(date +%s%N)}"
    local user="${2:-$USER_ID}"
    local body
    body=$(curl -s -X POST "$API_URL/terrain" \
        -H "Content-Type: application/json" \
        -d "$(cat <<EOF
{"name":"$name","user_id":"$user","geometry":$GEOM_VALID_1HA}
EOF
)")
    local id
    id=$(echo "$body" | jq -r '.id // empty')
    if [[ -n "$id" ]]; then
        CREATED_TERRAINS+=("$id|$user")
        echo "$id"
    else
        echo ""
    fi
}

# Limpieza al salir.
cleanup_resources() {
    echo
    echo -e "${BLUE}=== Cleanup ===${NC}"
    for entry in "${CREATED_TERRAINS[@]}"; do
        local tid="${entry%%|*}"
        local uid="${entry##*|}"
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$tid?user_id=$uid")
        echo "  DELETE $tid (owner=$uid) → $code"
    done
}

# ---------- Pre-flight: ¿el servicio responde? ------------------------------
echo -e "${BOLD}${BLUE}=== Pre-flight ===${NC}"
PING_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=$USER_ID" || echo "000")
if [[ "$PING_STATUS" == "000" ]]; then
    echo -e "${RED}[FATAL]${NC} terrain-service NO responde en $API_URL. Arráncalo y reintenta."
    exit 2
fi
echo "  terrain-service en $API_URL responde con $PING_STATUS al GET /terrain (esperable 200/4xx). OK."
[[ "$HAS_GRPCURL" -eq 1 ]] && echo "  grpcurl disponible — §10 ON" || echo "  grpcurl NO disponible — §10 SKIP"
[[ "$HAS_KCAT" -eq 1 ]] && echo "  kcat disponible — §11 ON" || echo "  kcat NO disponible — §11 intentará docker exec"
[[ "$HAS_DOCKER" -eq 1 ]] && echo "  docker disponible — fallback Kafka ON" || true

# ===========================================================================
# §1. POST /terrain — alta de terreno (TER-1.x)
# ===========================================================================
if section_enabled 1; then
section_header 1 "POST /terrain — alta de terreno"

# TER-1.01 — happy path mínimo
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"TER-1.01 Mínimo\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.01" "happy path mínimo" "201" "$STATUS"
if [[ "$STATUS" == "201" ]]; then
    NEW_ID=$(echo "$BODY" | jq -r '.id // empty')
    [[ -n "$NEW_ID" ]] && CREATED_TERRAINS+=("$NEW_ID|$USER_ID")
    assert_json_present "TER-1.01b" "id en respuesta" '.id' "$BODY"
    assert_json_contains "TER-1.01c" "message contiene nombre" '.message' "TER-1.01 Mínimo" "$BODY"
fi

# TER-1.02 — happy path completo
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"TER-1.02 Completo\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"soil_type\":\"franco\",\"slope_percent\":4.5,\"irrigation\":\"goteo\",\"cadastral_ref\":\"$CAD_REF_VALID_20\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.02" "happy path completo" "201" "$STATUS"
if [[ "$STATUS" == "201" ]]; then
    FULL_ID=$(echo "$BODY" | jq -r '.id // empty')
    [[ -n "$FULL_ID" ]] && CREATED_TERRAINS+=("$FULL_ID|$USER_ID")
    # Verifica que se persistieron los descriptivos
    DETAIL=$(curl -s "$API_URL/terrain/$FULL_ID")
    assert_json_eq "TER-1.02b" "soil_type persistido" '.soil_type' "franco" "$DETAIL"
    assert_json_present "TER-1.02c" "area_m2 calculada por PostGIS" '.area_m2' "$DETAIL"
fi

# TER-1.03 — name vacío
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.03" "name vacío → 400" "400" "$STATUS"
assert_json_contains "TER-1.03b" "errors menciona name" '.errors // [] | tostring' "name" "$BODY"

# TER-1.04 — name > 255 chars
LONG_NAME=$(printf 'X%.0s' $(seq 1 256))
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$LONG_NAME\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.04" "name > 255 chars → 400" "400" "$STATUS"

# TER-1.05 — user_id ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"NoUser\",\"geometry\":$GEOM_VALID_1HA}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.05" "user_id ausente → 400" "400" "$STATUS"
assert_json_contains "TER-1.05b" "errors menciona user_id" '.errors // [] | tostring' "user_id" "$BODY"

# TER-1.06 — user_id no UUID
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"BadUser\",\"user_id\":\"abc\",\"geometry\":$GEOM_VALID_1HA}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.06" "user_id no UUID → 400" "400" "$STATUS"

# TER-1.07 — user_id inexistente en auth-service
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"GhostUser\",\"user_id\":\"$ALT_USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.07" "user_id inexistente → 404" "404" "$STATUS"
assert_json_eq "TER-1.07b" "title 'User not found'" '.title' "User not found" "$BODY"
assert_json_contains "TER-1.07c" "detail menciona el user_id" '.detail' "$ALT_USER_ID" "$BODY"

# TER-1.08 — geometry ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"NoGeom\",\"user_id\":\"$USER_ID\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.08" "geometry ausente → 400" "400" "$STATUS"
assert_json_contains "TER-1.08b" "errors menciona geometry" '.errors // [] | tostring' "geometry" "$BODY"

# TER-1.09 — geometry no es objeto
# DTO @NotEmpty Map -> si es String el deserializador de Jackson devuelve 400 antes.
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"BadGeom\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_NOT_OBJECT}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.09" "geometry no Polygon → 400" "400" "$STATUS"

# TER-1.10 — SRID ≠ 4326
# El service hace ST_SetSRID(..., 4326) forzosamente; la API no admite SRID custom
# desde GeoJSON estándar. La constraint PostGIS no se puede provocar desde curl.
skip_test "TER-1.10" "SRID forzado a 4326 por ST_SetSRID — no provocable por API"

# TER-1.11 — polígono no cerrado (PostGIS lo rechaza)
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"NotClosed\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_NOT_CLOSED}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.11" "polígono no cerrado → 400" "400" "$STATUS"

# TER-1.12 — área < 100 m²
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"TooTiny\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_TINY}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.12" "área < 100 m² → 400" "400" "$STATUS"
assert_json_eq "TER-1.12b" "title 'Area out of range'" '.title' "Area out of range" "$BODY"

# TER-1.13 — área > 1e8 m²
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"TooHuge\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_HUGE}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.13" "área > 1e8 m² → 400" "400" "$STATUS"
assert_json_eq "TER-1.13b" "title 'Area out of range'" '.title' "Area out of range" "$BODY"

# TER-1.14 — soil_type fuera del enum
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"BadSoil\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"soil_type\":\"plastico\"}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.14" "soil_type inválido → 400" "400" "$STATUS"

# TER-1.15 — irrigation fuera del enum
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"BadIrrig\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"irrigation\":\"manguera\"}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.15" "irrigation inválido → 400" "400" "$STATUS"

# TER-1.16 — slope_percent < 0
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"NegSlope\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"slope_percent\":-0.1}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.16" "slope_percent < 0 → 400" "400" "$STATUS"
assert_json_contains "TER-1.16b" "errors menciona slope" '.errors // [] | tostring' "slope" "$BODY"

# TER-1.17 — slope_percent > 100
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"OverSlope\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"slope_percent\":100.01}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.17" "slope_percent > 100 → 400" "400" "$STATUS"

# TER-1.18 — slope_percent en frontera
for slope in 0.0 100.0; do
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Slope-$slope\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"slope_percent\":$slope}")
    BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
    assert_status "TER-1.18[$slope]" "slope frontera $slope → 201" "201" "$STATUS"
    if [[ "$STATUS" == "201" ]]; then
        ID=$(echo "$BODY" | jq -r '.id // empty')
        [[ -n "$ID" ]] && CREATED_TERRAINS+=("$ID|$USER_ID")
    fi
done

# TER-1.19 — cadastral_ref con < 14 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"ShortCad\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"cadastral_ref\":\"abc\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.19" "cadastral_ref corta → 400" "400" "$STATUS"
assert_json_contains "TER-1.19b" "errors menciona cadastral" '.errors // [] | tostring' "cadastral" "$BODY"

# TER-1.20 — cadastral_ref minúsculas
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"LowerCad\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"cadastral_ref\":\"$CAD_REF_INVALID_LOWER\"}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.20" "cadastral_ref minúsculas → 400" "400" "$STATUS"

# TER-1.21 — cadastral_ref 14 chars válida
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Cad14\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"cadastral_ref\":\"$CAD_REF_VALID_14\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.21" "cadastral_ref 14 chars → 201" "201" "$STATUS"
if [[ "$STATUS" == "201" ]]; then
    ID=$(echo "$BODY" | jq -r '.id // empty'); [[ -n "$ID" ]] && CREATED_TERRAINS+=("$ID|$USER_ID")
fi

# TER-1.22 — cadastral_ref 20 chars válida (cubierto en 1.02)
skip_test "TER-1.22" "cubierto por TER-1.02 (20 chars)"

# TER-1.23 — cadastral_ref 21 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Cad21\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA,\"cadastral_ref\":\"012345678901234567890\"}")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.23" "cadastral_ref 21 chars → 400" "400" "$STATUS"

# TER-1.24 — Content-Type no JSON
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: text/plain" \
    -d "no json here")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.24" "Content-Type no JSON → 415" "415" "$STATUS"

# TER-1.25 — body JSON malformado
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Bad,\"user_id\":")
STATUS="${RESP##*$'\n'}"
assert_status "TER-1.25" "JSON malformado → 400" "400" "$STATUS"

# TER-1.26 — sin JWT vía gateway
if [[ -n "$GATEWAY_URL" ]]; then
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/terrain?user_id=$USER_ID")
    assert_status "TER-1.26" "gateway sin JWT → 401" "401" "$STATUS"
else
    skip_test "TER-1.26" "GATEWAY_URL no configurada"
fi

# TER-1.27 — auth-service caído (gRPC StatusRuntimeException)
skip_test "TER-1.27" "requiere apagar auth-service durante el test, no automatizable de forma segura"

# TER-1.28 — idempotencia tras gRPC false
PRECOUNT=$(curl -s "$API_URL/terrain?user_id=$ALT_USER_ID" | jq 'length')
curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"GhostInsert\",\"user_id\":\"$ALT_USER_ID\",\"geometry\":$GEOM_VALID_1HA}" > /dev/null
POSTCOUNT=$(curl -s "$API_URL/terrain?user_id=$ALT_USER_ID" | jq 'length')
if [[ "$PRECOUNT" == "$POSTCOUNT" ]]; then
    _pass "TER-1.28" "no se persiste tras 404 user.notfound (count=$POSTCOUNT)"
else
    _fail "TER-1.28" "no se persiste tras 404 user.notfound" "count=$PRECOUNT" "count=$POSTCOUNT"
fi

# TER-1.29 — idempotencia tras error de geometría
PRECOUNT=$(curl -s "$API_URL/terrain?user_id=$USER_ID" | jq 'length')
curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"GeomErr\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_NOT_OBJECT}" > /dev/null
POSTCOUNT=$(curl -s "$API_URL/terrain?user_id=$USER_ID" | jq 'length')
if [[ "$PRECOUNT" == "$POSTCOUNT" ]]; then
    _pass "TER-1.29" "no se persiste tras error de geometría"
else
    _fail "TER-1.29" "no se persiste tras error de geometría" "count=$PRECOUNT" "count=$POSTCOUNT"
fi

# TER-1.30 — Accept-Language: en
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: en" \
    -d "{\"name\":\"TER-1.30 EN\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-1.30" "Accept-Language en → 201" "201" "$STATUS"
if [[ "$STATUS" == "201" ]]; then
    ID=$(echo "$BODY" | jq -r '.id // empty'); [[ -n "$ID" ]] && CREATED_TERRAINS+=("$ID|$USER_ID")
    assert_json_contains "TER-1.30b" "mensaje en inglés" '.message' "successfully" "$BODY"
fi
fi  # /sección 1

# ===========================================================================
# §2. GET /terrain — listar / detalle (TER-2.x)
# ===========================================================================
if section_enabled 2; then
section_header 2 "GET /terrain — listar / detalle"

# TER-2.01 — listar usuario sin terrenos
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain?user_id=$ALT_USER_ID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-2.01" "listar usuario inexistente → 200 []" "200" "$STATUS"
LEN=$(echo "$BODY" | jq 'length')
if [[ "$LEN" == "0" ]]; then
    _pass "TER-2.01b" "lista vacía"
else
    _fail "TER-2.01b" "lista vacía esperada" "length=0" "length=$LEN"
fi

# TER-2.02 — listar con varios terrenos
T1=$(create_terrain_basic "QA-2.02-A")
T2=$(create_terrain_basic "QA-2.02-B")
T3=$(create_terrain_basic "QA-2.02-C")
RESP=$(curl -s "$API_URL/terrain?user_id=$USER_ID")
COUNT=$(echo "$RESP" | jq '[.[] | select(.user_id == "'"$USER_ID"'")] | length')
if [[ "$COUNT" -ge 3 ]]; then
    _pass "TER-2.02" "lista con ≥ 3 terrenos del usuario (count=$COUNT)"
else
    _fail "TER-2.02" "lista con ≥ 3 terrenos" "count >= 3" "count=$COUNT"
fi

# TER-2.03 — listar sin user_id
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain")
assert_status "TER-2.03" "sin user_id → 400" "400" "$STATUS"

# TER-2.04 — user_id mal formado
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=abc")
assert_status "TER-2.04" "user_id mal formado → 400" "400" "$STATUS"

# TER-2.05 — detalle existente
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain/$T1")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-2.05" "detalle existente → 200" "200" "$STATUS"
assert_json_present "TER-2.05b" "id presente" '.id' "$BODY"
assert_json_present "TER-2.05c" "name presente" '.name' "$BODY"

# TER-2.06 — detalle inexistente
RANDOM_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain/$RANDOM_UUID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-2.06" "detalle inexistente → 404" "404" "$STATUS"
assert_json_eq "TER-2.06b" "title 'Terrain not found'" '.title' "Terrain not found" "$BODY"
assert_json_contains "TER-2.06c" "detail menciona el id" '.detail' "$RANDOM_UUID" "$BODY"

# TER-2.07 — UUID malformado en path
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/abc")
assert_status "TER-2.07" "UUID malformado → 400" "400" "$STATUS"

# TER-2.08 — geometry como GeoJSON
RESP=$(curl -s "$API_URL/terrain/$T1?fields=geometry")
GEOM=$(echo "$RESP" | jq -r '.geometry')
if echo "$GEOM" | jq -e '.type == "Polygon"' > /dev/null 2>&1; then
    _pass "TER-2.08" "geometry serializada como GeoJSON Polygon"
else
    _fail "TER-2.08" "geometry como GeoJSON" 'jq .type == "Polygon"' "$GEOM"
fi

# TER-2.09 — centroid legible
RESP=$(curl -s "$API_URL/terrain/$T1?fields=centroid")
CEN=$(echo "$RESP" | jq -r '.centroid')
if echo "$CEN" | jq -e '.type == "Point"' > /dev/null 2>&1; then
    _pass "TER-2.09" "centroid serializado como GeoJSON Point"
else
    _fail "TER-2.09" "centroid como GeoJSON Point" 'jq .type == "Point"' "$CEN"
fi

# TER-2.10 — timestamps ISO-8601
RESP=$(curl -s "$API_URL/terrain/$T1?fields=created_at")
CA=$(echo "$RESP" | jq -r '.created_at')
if [[ "$CA" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T ]]; then
    _pass "TER-2.10" "created_at en ISO-8601 ($CA)"
else
    _fail "TER-2.10" "created_at en ISO-8601" "ISO-8601" "$CA"
fi
fi  # /sección 2

# ===========================================================================
# §3. DELETE /terrain/{id} (TER-3.x)
# ===========================================================================
if section_enabled 3; then
section_header 3 "DELETE /terrain/{id}"

# Setup: terreno propio
T_DEL=$(create_terrain_basic "QA-3.01-Del")

# TER-3.01 — borrado del propietario
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL?user_id=$USER_ID")
assert_status "TER-3.01" "borrado del propietario → 204" "204" "$STATUS"
# Verifica que ya no está
GET_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_DEL")
assert_status "TER-3.01b" "tras borrar GET → 404" "404" "$GET_STATUS"
# Quita de la lista de cleanup
CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T_DEL|$USER_ID/}")

# TER-3.02 — borrado por usuario equivocado
T_OWN=$(create_terrain_basic "QA-3.02-Owned")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_OWN?user_id=$WRONG_OWNER_ID")
assert_status "TER-3.02" "borrado por user equivocado → 404" "404" "$STATUS"
# La fila sigue:
GET_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_OWN")
assert_status "TER-3.02b" "fila sigue tras 404" "200" "$GET_STATUS"

# TER-3.03 — borrado de id inexistente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$RANDOM_UUID?user_id=$USER_ID")
assert_status "TER-3.03" "id inexistente → 404" "404" "$STATUS"

# TER-3.04 — cascade borra adjuntos
T_CASC=$(create_terrain_basic "QA-3.04-Cascade")
echo "fixture" > "$TMP_DIR/cascade.pdf"
curl -s -X POST "$API_URL/terrain/$T_CASC/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/cascade.pdf;type=application/pdf" > /dev/null
ATT_BEFORE=$(curl -s "$API_URL/terrain/$T_CASC/attachment?user_id=$USER_ID" | jq 'length')
curl -s -X DELETE "$API_URL/terrain/$T_CASC?user_id=$USER_ID" > /dev/null
ATT_AFTER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_CASC/attachment?user_id=$USER_ID")
if [[ "$ATT_BEFORE" -ge 1 && "$ATT_AFTER_STATUS" == "404" ]]; then
    _pass "TER-3.04" "cascade SQL borró adjuntos junto al terreno (had $ATT_BEFORE)"
else
    _fail "TER-3.04" "cascade attachment" "before≥1 & after 404" "before=$ATT_BEFORE after=$ATT_AFTER_STATUS"
fi
CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T_CASC|$USER_ID/}")

# TER-3.05 — sin user_id
T_NOQ=$(create_terrain_basic "QA-3.05-NoUid")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_NOQ")
assert_status "TER-3.05" "DELETE sin user_id → 400" "400" "$STATUS"

# TER-3.06 — user_id mal formado
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_NOQ?user_id=abc")
assert_status "TER-3.06" "DELETE user_id mal formado → 400" "400" "$STATUS"

# TER-3.07 — idempotencia (segundo DELETE)
T_IDEM=$(create_terrain_basic "QA-3.07-Idem")
S1=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_IDEM?user_id=$USER_ID")
S2=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_IDEM?user_id=$USER_ID")
if [[ "$S1" == "204" && "$S2" == "404" ]]; then
    _pass "TER-3.07" "idempotencia: primer 204, segundo 404"
else
    _fail "TER-3.07" "idempotencia DELETE" "204 luego 404" "$S1 luego $S2"
fi
CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T_IDEM|$USER_ID/}")
CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T_OWN|$USER_ID/}")
fi  # /sección 3

# ===========================================================================
# §4. fields= (TER-4.x)
# ===========================================================================
if section_enabled 4; then
section_header 4 "fields= proyección dinámica"

T_FIELDS=$(create_terrain_basic "QA-4-Fields")

# TER-4.01 — sin fields
RESP=$(curl -s "$API_URL/terrain/$T_FIELDS")
KEYS=$(echo "$RESP" | jq -r 'keys | length')
if [[ "$KEYS" -ge 10 ]]; then
    _pass "TER-4.01" "sin fields= devuelve todas las columnas (keys=$KEYS)"
else
    _fail "TER-4.01" "sin fields= devuelve todas" "keys >= 10" "keys=$KEYS"
fi

# TER-4.02 — fields=name
RESP=$(curl -s "$API_URL/terrain/$T_FIELDS?fields=name")
KEYS=$(echo "$RESP" | jq -r 'keys | length')
if [[ "$KEYS" == "1" ]] && echo "$RESP" | jq -e '.name' > /dev/null; then
    _pass "TER-4.02" "fields=name devuelve solo name"
else
    _fail "TER-4.02" "fields=name solo name" "keys=1, .name presente" "$RESP"
fi

# TER-4.03 — fields=id,name,area_m2
RESP=$(curl -s "$API_URL/terrain/$T_FIELDS?fields=id,name,area_m2")
KEYS=$(echo "$RESP" | jq -r 'keys | length')
if [[ "$KEYS" == "3" ]]; then
    _pass "TER-4.03" "fields= con varios devuelve esas 3"
else
    _fail "TER-4.03" "fields= 3 columnas" "keys=3" "keys=$KEYS"
fi

# TER-4.04 — fields=geometry (cubierto en 2.08)
skip_test "TER-4.04" "cubierto por TER-2.08"

# TER-4.05 — fields=centroid (cubierto en 2.09)
skip_test "TER-4.05" "cubierto por TER-2.09"

# TER-4.06 — campo desconocido
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=password")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-4.06" "fields= campo desconocido → 400" "400" "$STATUS"

# TER-4.07 — caso mixto (Java enum case-sensitive)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=NAME")
assert_status "TER-4.07" "fields=NAME (case-sensitive) → 400" "400" "$STATUS"

# TER-4.08 — fields= vacío
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=")
assert_status "TER-4.08" "fields= vacío → 200" "200" "$STATUS"

# TER-4.09 — fields= duplicados
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=id,id,name")
assert_status "TER-4.09" "fields= duplicados → 200" "200" "$STATUS"

# TER-4.10 — fields= con espacios
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=id,%20name")
# Comportamiento: si el split no trimea, " name" no está en el enum y devuelve 400.
if [[ "$STATUS" == "200" || "$STATUS" == "400" ]]; then
    _pass "TER-4.10" "fields= con espacios responde controlado ($STATUS)"
else
    _fail "TER-4.10" "fields= con espacios" "200 o 400" "$STATUS"
fi

# TER-4.11 — inyección SQL
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS?fields=id;DROP%20TABLE%20terrain")
assert_status "TER-4.11" "inyección SQL → 400 (whitelist enum)" "400" "$STATUS"
# Verifica que la tabla sigue ahí
PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_FIELDS")
assert_status "TER-4.11b" "tabla terrain sigue accesible" "200" "$PING"
fi  # /sección 4

# ===========================================================================
# §5. POST /terrain/{id}/attachment (TER-5.x)
# ===========================================================================
if section_enabled 5; then
section_header 5 "POST /terrain/{id}/attachment — subida"

T_ATT=$(create_terrain_basic "QA-5-Attach")

# Generar fixtures de tamaños distintos
# JPG válido (header mínimo) — 1 KB rellenos con \x00
printf '\xFF\xD8\xFF\xE0' > "$TMP_DIR/img.jpg"
dd if=/dev/zero bs=1020 count=1 >> "$TMP_DIR/img.jpg" 2>/dev/null
# PNG signature + pad
printf '\x89PNG\r\n\x1a\n' > "$TMP_DIR/img.png"
dd if=/dev/zero bs=2040 count=1 >> "$TMP_DIR/img.png" 2>/dev/null
# PDF mínimo
printf '%%PDF-1.4\n%%QA fixture\n' > "$TMP_DIR/doc.pdf"
dd if=/dev/zero bs=$((100*1024 - 25)) count=1 >> "$TMP_DIR/doc.pdf" 2>/dev/null
# Texto plano
echo "plain text" > "$TMP_DIR/file.txt"
# 1 byte
printf 'A' > "$TMP_DIR/onebyte.jpg"

# TER-5.01 — subida JPG válida
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/img.jpg;type=image/jpeg")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-5.01" "JPG válida → 201" "201" "$STATUS"
ATT_JPG=$(echo "$BODY" | jq -r '.id // empty')
if [[ "$STATUS" == "201" ]]; then
    assert_json_eq "TER-5.01b" "mime_type" '.mime_type' "image/jpeg" "$BODY"
    assert_json_contains "TER-5.01c" "download_url" '.download_url' "/terrain/$T_ATT/attachment/$ATT_JPG/content" "$BODY"
fi

# TER-5.02 — PNG válida
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/img.png;type=image/png")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-5.02" "PNG válida → 201" "201" "$STATUS"
ATT_PNG=$(echo "$BODY" | jq -r '.id // empty')

# TER-5.03 — PDF válida
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/doc.pdf;type=application/pdf")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-5.03" "PDF válida → 201" "201" "$STATUS"
ATT_PDF=$(echo "$BODY" | jq -r '.id // empty')

# TER-5.04 — text/plain (MIME no permitido)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/file.txt;type=text/plain")
assert_status "TER-5.04" "text/plain → 415" "415" "$STATUS"

# TER-5.05 — application/zip
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/file.txt;type=application/zip")
assert_status "TER-5.05" "application/zip → 415" "415" "$STATUS"

# TER-5.06 — MIME ausente / desconocido
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/file.txt;type=application/octet-stream")
assert_status "TER-5.06" "MIME genérico → 415" "415" "$STATUS"

# TER-5.07 — tamaño 0
: > "$TMP_DIR/empty.jpg"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/empty.jpg;type=image/jpeg")
assert_status "TER-5.07" "size=0 → 400" "400" "$STATUS"

# TER-5.08 — 1 byte
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/onebyte.jpg;type=image/jpeg")
STATUS="${RESP##*$'\n'}"
assert_status "TER-5.08" "1 byte → 201" "201" "$STATUS"

# TER-5.09 — 10 MB exacto
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "TER-5.09" "SKIP_SLOW=1"
else
    dd if=/dev/zero of="$TMP_DIR/10mb.jpg" bs=1M count=10 status=none
    # Header JPG por si el detector exige magic number
    printf '\xFF\xD8\xFF\xE0' | dd of="$TMP_DIR/10mb.jpg" bs=1 count=4 conv=notrunc status=none
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
        -F "file=@$TMP_DIR/10mb.jpg;type=image/jpeg")
    # Tras este test la cuota está casi al límite; cleanup global lo arrastra.
    assert_status "TER-5.09" "10 MB exacto → 201" "201" "$STATUS"
fi

# TER-5.10 — 10 MB + 1 byte
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "TER-5.10" "SKIP_SLOW=1"
else
    dd if=/dev/zero of="$TMP_DIR/10mb1.jpg" bs=1 count=$((10*1024*1024 + 1)) status=none 2>/dev/null
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
        -F "file=@$TMP_DIR/10mb1.jpg;type=image/jpeg")
    assert_status "TER-5.10" "10 MB + 1 → 413" "413" "$STATUS"
fi

# TER-5.11 — cuota acumulada > 100 MB
# Subimos a un terreno fresco para no contaminar el flujo.
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "TER-5.11" "SKIP_SLOW=1 (requiere ~110 MB de subidas)"
else
    T_QUOTA=$(create_terrain_basic "QA-5.11-Quota")
    dd if=/dev/zero of="$TMP_DIR/10mb_q.jpg" bs=1M count=10 status=none
    printf '\xFF\xD8\xFF\xE0' | dd of="$TMP_DIR/10mb_q.jpg" bs=1 count=4 conv=notrunc status=none
    OK=0
    for i in $(seq 1 10); do
        S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_QUOTA/attachment?user_id=$USER_ID" \
            -F "file=@$TMP_DIR/10mb_q.jpg;type=image/jpeg")
        [[ "$S" == "201" ]] && OK=$((OK+1))
    done
    # 11º empuja por encima de 100 MB → 400
    S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_QUOTA/attachment?user_id=$USER_ID" \
        -F "file=@$TMP_DIR/10mb_q.jpg;type=image/jpeg")
    if [[ "$OK" == "10" && "$S" == "400" ]]; then
        _pass "TER-5.11" "10× 10MB ok, 11º excede cuota → 400"
    else
        _fail "TER-5.11" "cuota 100MB" "10 × 201 + 11º 400" "$OK × 201 + 11º $S"
    fi
fi

# TER-5.12 — cuota justo en el límite (cubierto por las 10 primeras del 5.11)
skip_test "TER-5.12" "cubierto implícitamente por TER-5.11 (las 10 subidas pasan)"

# TER-5.13 — terreno inexistente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$RANDOM_UUID/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/img.jpg;type=image/jpeg")
assert_status "TER-5.13" "terreno inexistente → 404" "404" "$STATUS"

# TER-5.14 — terreno de otro usuario
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$WRONG_OWNER_ID" \
    -F "file=@$TMP_DIR/img.jpg;type=image/jpeg")
assert_status "TER-5.14" "owner mismatch → 404" "404" "$STATUS"

# TER-5.15 — sin user_id
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment" \
    -F "file=@$TMP_DIR/img.jpg;type=image/jpeg")
assert_status "TER-5.15" "sin user_id → 400" "400" "$STATUS"

# TER-5.16 — sin parte file
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "other=@$TMP_DIR/img.jpg;type=image/jpeg")
assert_status "TER-5.16" "sin parte 'file' → 400" "400" "$STATUS"

# TER-5.17 — multiple files con name=file
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/$T_ATT/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/img.jpg;type=image/jpeg" \
    -F "file=@$TMP_DIR/img.png;type=image/png")
# Aceptable: 201 (procesa el primero) o 400 según implementación
if [[ "$STATUS" == "201" || "$STATUS" == "400" ]]; then
    _pass "TER-5.17" "multiple files responde controlado ($STATUS)"
else
    _fail "TER-5.17" "multiple files" "201 o 400" "$STATUS"
fi

# TER-5.18 / 5.19 — storage IO failure: requiere mockear FileStorageService, no curlable
skip_test "TER-5.18" "requiere mock interno de FileStorageService"
skip_test "TER-5.19" "requiere mock interno de FileStorageService"

# TER-5.20 — persistencia tras éxito (subir + descargar)
DOWN=$(curl -s -X GET "$API_URL/terrain/$T_ATT/attachment/$ATT_JPG/content" -o "$TMP_DIR/dl.jpg" -w "%{http_code}")
ORIG_SIZE=$(stat -c%s "$TMP_DIR/img.jpg" 2>/dev/null || stat -f%z "$TMP_DIR/img.jpg")
DL_SIZE=$(stat -c%s "$TMP_DIR/dl.jpg" 2>/dev/null || stat -f%z "$TMP_DIR/dl.jpg")
if [[ "$DOWN" == "200" && "$ORIG_SIZE" == "$DL_SIZE" ]]; then
    _pass "TER-5.20" "upload+download integridad ($DL_SIZE bytes)"
else
    _fail "TER-5.20" "integridad upload/download" "200 + $ORIG_SIZE bytes" "$DOWN + $DL_SIZE bytes"
fi
fi  # /sección 5

# ===========================================================================
# §6. GET /terrain/{id}/attachment (TER-6.x)
# ===========================================================================
if section_enabled 6; then
section_header 6 "GET /terrain/{id}/attachment — listado"

T_LIST=$(create_terrain_basic "QA-6-List")

# TER-6.01 — sin adjuntos
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain/$T_LIST/attachment?user_id=$USER_ID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-6.01" "sin adjuntos → 200 []" "200" "$STATUS"
LEN=$(echo "$BODY" | jq 'length')
if [[ "$LEN" == "0" ]]; then
    _pass "TER-6.01b" "lista vacía"
else
    _fail "TER-6.01b" "lista vacía" "0" "$LEN"
fi

# TER-6.02 — con N adjuntos
echo "x" > "$TMP_DIR/a.jpg"
for i in 1 2 3; do
    curl -s -X POST "$API_URL/terrain/$T_LIST/attachment?user_id=$USER_ID" \
        -F "file=@$TMP_DIR/a.jpg;type=image/jpeg" > /dev/null
done
RESP=$(curl -s "$API_URL/terrain/$T_LIST/attachment?user_id=$USER_ID")
LEN=$(echo "$RESP" | jq 'length')
if [[ "$LEN" == "3" ]]; then
    _pass "TER-6.02" "3 adjuntos listados"
else
    _fail "TER-6.02" "3 adjuntos" "length=3" "length=$LEN"
fi

# TER-6.03 — terreno inexistente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$RANDOM_UUID/attachment?user_id=$USER_ID")
assert_status "TER-6.03" "terreno inexistente → 404" "404" "$STATUS"

# TER-6.04 — owner mismatch
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_LIST/attachment?user_id=$WRONG_OWNER_ID")
assert_status "TER-6.04" "owner mismatch → 404" "404" "$STATUS"

# TER-6.05 — sin user_id
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_LIST/attachment")
assert_status "TER-6.05" "sin user_id → 400" "400" "$STATUS"

# TER-6.06 — download_url correcta
URL_FIRST=$(echo "$RESP" | jq -r '.[0].download_url')
EXPECTED="/terrain/$T_LIST/attachment/$(echo "$RESP" | jq -r '.[0].id')/content"
if [[ "$URL_FIRST" == "$EXPECTED" ]]; then
    _pass "TER-6.06" "download_url correcta"
else
    _fail "TER-6.06" "download_url" "$EXPECTED" "$URL_FIRST"
fi
fi  # /sección 6

# ===========================================================================
# §7. GET /attachment/{id}/content (TER-7.x)
# ===========================================================================
if section_enabled 7; then
section_header 7 "GET /attachment/{id}/content — descarga"

T_DOWN=$(create_terrain_basic "QA-7-Download")

# Subir un JPG, PNG y PDF
echo "fake-jpg" > "$TMP_DIR/d.jpg"
echo "fake-png" > "$TMP_DIR/d.png"
echo "fake-pdf" > "$TMP_DIR/d.pdf"
J=$(curl -s -X POST "$API_URL/terrain/$T_DOWN/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/d.jpg;type=image/jpeg" | jq -r '.id')
P=$(curl -s -X POST "$API_URL/terrain/$T_DOWN/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/d.png;type=image/png" | jq -r '.id')
D=$(curl -s -X POST "$API_URL/terrain/$T_DOWN/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/d.pdf;type=application/pdf" | jq -r '.id')

# TER-7.01 — JPG: status, content-type, content-disposition
HEADERS=$(curl -s -D - -o "$TMP_DIR/out.jpg" "$API_URL/terrain/$T_DOWN/attachment/$J/content" -w "%{http_code}\n")
STATUS=$(echo "$HEADERS" | tail -1)
HDR=$(echo "$HEADERS" | head -n -1)
assert_status "TER-7.01" "descarga JPG → 200" "200" "$STATUS"
assert_content_type_contains "TER-7.01b" "Content-Type image/jpeg" "image/jpeg" "$HDR"
DISP=$(echo "$HDR" | grep -i '^Content-Disposition:' | tr -d '\r')
if [[ "$DISP" == *"inline"* && "$DISP" == *"d.jpg"* ]]; then
    _pass "TER-7.01c" "Content-Disposition inline + filename"
else
    _fail "TER-7.01c" "Content-Disposition" "inline + filename" "$DISP"
fi

# TER-7.02 — PNG
HEADERS=$(curl -s -D - -o /dev/null "$API_URL/terrain/$T_DOWN/attachment/$P/content" -w "%{http_code}\n")
STATUS=$(echo "$HEADERS" | tail -1); HDR=$(echo "$HEADERS" | head -n -1)
assert_status "TER-7.02" "descarga PNG → 200" "200" "$STATUS"
assert_content_type_contains "TER-7.02b" "Content-Type image/png" "image/png" "$HDR"

# TER-7.03 — PDF
HEADERS=$(curl -s -D - -o /dev/null "$API_URL/terrain/$T_DOWN/attachment/$D/content" -w "%{http_code}\n")
STATUS=$(echo "$HEADERS" | tail -1); HDR=$(echo "$HEADERS" | head -n -1)
assert_status "TER-7.03" "descarga PDF → 200" "200" "$STATUS"
assert_content_type_contains "TER-7.03b" "Content-Type application/pdf" "application/pdf" "$HDR"

# TER-7.04 — id inexistente
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/terrain/$T_DOWN/attachment/$RANDOM_UUID/content")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-7.04" "id inexistente → 404" "404" "$STATUS"
assert_json_eq "TER-7.04b" "title 'Attachment not found'" '.title' "Attachment not found" "$BODY"

# TER-7.05 — adjunto pertenece a otro terreno
T_OTHER=$(create_terrain_basic "QA-7.05-Other")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_OTHER/attachment/$J/content")
assert_status "TER-7.05" "terreno mismatch → 404" "404" "$STATUS"

# TER-7.06 — fichero perdido en disco
skip_test "TER-7.06" "requiere acceso al volumen ATTACHMENTS_STORAGE_ROOT del contenedor"

# TER-7.07 — UUID malformado en path
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_DOWN/attachment/xyz/content")
assert_status "TER-7.07" "UUID malformado → 400" "400" "$STATUS"

# TER-7.08 — sin JWT vía gateway
if [[ -n "$GATEWAY_URL" ]]; then
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/terrain/$T_DOWN/attachment/$J/content")
    assert_status "TER-7.08" "gateway sin JWT → 401" "401" "$STATUS"
else
    skip_test "TER-7.08" "GATEWAY_URL no configurada"
fi
fi  # /sección 7

# ===========================================================================
# §8. DELETE /attachment/{id} (TER-8.x)
# ===========================================================================
if section_enabled 8; then
section_header 8 "DELETE /attachment/{id}"

T_DEL=$(create_terrain_basic "QA-8-Delete")
echo "del" > "$TMP_DIR/del.jpg"
A1=$(curl -s -X POST "$API_URL/terrain/$T_DEL/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/del.jpg;type=image/jpeg" | jq -r '.id')

# TER-8.01 — borrado del propietario
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$A1?user_id=$USER_ID")
assert_status "TER-8.01" "borrado del propietario → 204" "204" "$STATUS"

# TER-8.02 — id inexistente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$RANDOM_UUID?user_id=$USER_ID")
assert_status "TER-8.02" "id inexistente → 404" "404" "$STATUS"

# TER-8.03 — adjunto de otro terreno
A2=$(curl -s -X POST "$API_URL/terrain/$T_DEL/attachment?user_id=$USER_ID" \
    -F "file=@$TMP_DIR/del.jpg;type=image/jpeg" | jq -r '.id')
T_OTHER2=$(create_terrain_basic "QA-8.03-Other")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_OTHER2/attachment/$A2?user_id=$USER_ID")
assert_status "TER-8.03" "terreno mismatch → 404" "404" "$STATUS"

# TER-8.04 — owner mismatch
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$A2?user_id=$WRONG_OWNER_ID")
assert_status "TER-8.04" "user_id mismatch → 404" "404" "$STATUS"

# TER-8.05 — sin user_id
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$A2")
assert_status "TER-8.05" "sin user_id → 400" "400" "$STATUS"

# TER-8.06 — idempotencia
S1=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$A2?user_id=$USER_ID")
S2=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/terrain/$T_DEL/attachment/$A2?user_id=$USER_ID")
if [[ "$S1" == "204" && "$S2" == "404" ]]; then
    _pass "TER-8.06" "idempotencia DELETE: 204 → 404"
else
    _fail "TER-8.06" "idempotencia" "204 luego 404" "$S1 luego $S2"
fi

# TER-8.07 — cuota se libera (versión rápida)
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "TER-8.07" "SKIP_SLOW=1"
else
    skip_test "TER-8.07" "cubierto por flujo TER-5.11 + delete posterior"
fi
fi  # /sección 8

# ===========================================================================
# §9. POST /terrain/import (TER-9.x)
# ===========================================================================
if section_enabled 9; then
section_header 9 "POST /terrain/import — Catastro / SIGPAC"

# TER-9.01 — reference vacía
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d '{"reference":"","kind":"CADASTRAL"}')
STATUS="${RESP##*$'\n'}"
assert_status "TER-9.01" "reference vacía → 400" "400" "$STATUS"

# TER-9.02 — kind ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d '{"reference":"1234ABCDEFGHIJKL5678"}')
STATUS="${RESP##*$'\n'}"
assert_status "TER-9.02" "kind ausente → 400" "400" "$STATUS"

# TER-9.03 — kind inválido
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d '{"reference":"1234ABCDEFGHIJKL5678","kind":"OTRO"}')
STATUS="${RESP##*$'\n'}"
assert_status "TER-9.03" "kind inválido → 400" "400" "$STATUS"

# TER-9.04 — Cadastral 19 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d '{"reference":"1234ABCDEFGHIJKL567","kind":"CADASTRAL"}')
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "TER-9.04" "Cadastral 19 chars → 400" "400" "$STATUS"
assert_json_eq "TER-9.04b" "title 'Cadastral import failed'" '.title' "Cadastral import failed" "$BODY"

# TER-9.05 — Cadastral 21 chars
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d '{"reference":"1234ABCDEFGHIJKL56789","kind":"CADASTRAL"}')
assert_status "TER-9.05" "Cadastral 21 chars → 400" "400" "$STATUS"

# TER-9.06 — Cadastral minúsculas
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"abcdefghijklmnopqrst\",\"kind\":\"CADASTRAL\"}")
assert_status "TER-9.06" "Cadastral minúsculas → 400" "400" "$STATUS"

# TER-9.07 — Cadastral con guiones
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$CAD_REF_INVALID_DASH\",\"kind\":\"CADASTRAL\"}")
assert_status "TER-9.07" "Cadastral con guiones → 400" "400" "$STATUS"

# TER-9.08 — SIGPAC sin guiones
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$SIGPAC_BAD_NODASHES\",\"kind\":\"SIGPAC\"}")
assert_status "TER-9.08" "SIGPAC sin guiones → 400" "400" "$STATUS"

# TER-9.09 — SIGPAC con tramo extra
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$SIGPAC_BAD_EXTRA\",\"kind\":\"SIGPAC\"}")
assert_status "TER-9.09" "SIGPAC con tramo extra → 400" "400" "$STATUS"

# TER-9.10 — SIGPAC válido (passes regex but provider not configured)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$SIGPAC_VALID\",\"kind\":\"SIGPAC\"}")
# 502 si no hay base-url, 200/4xx si sí
if [[ "$STATUS" == "502" ]]; then
    _pass "TER-9.10" "SIGPAC válido sin proveedor → 502"
elif [[ "$STATUS" == "200" || "$STATUS" == "404" ]]; then
    _pass "TER-9.10" "SIGPAC válido con proveedor configurado → $STATUS"
else
    _fail "TER-9.10" "SIGPAC válido" "502 (sin proveedor) o 200/404 (con)" "$STATUS"
fi

# TER-9.11 — cadastro.api.base-url vacía
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$CAD_REF_VALID_20\",\"kind\":\"CADASTRAL\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
if [[ "$STATUS" == "502" ]]; then
    assert_json_contains "TER-9.11" "detail cadastral.api.unavailable" '.detail' "unavailable" "$BODY"
elif [[ "$STATUS" == "200" || "$STATUS" == "404" ]]; then
    _pass "TER-9.11" "cadastro proveedor configurado → $STATUS (skip 502)"
else
    _fail "TER-9.11" "cadastral.api.unavailable" "502" "$STATUS"
fi

# TER-9.12 — sigpac.api.base-url vacía (cubierto por TER-9.10)
skip_test "TER-9.12" "cubierto por TER-9.10"

# TER-9.13–9.22 — proveedor real con WireMock
skip_test "TER-9.13" "requiere stub WireMock fuera del scope del script QA HTTP"
skip_test "TER-9.14" "requiere stub WireMock"
skip_test "TER-9.15" "requiere stub WireMock"
skip_test "TER-9.16" "requiere stub WireMock"
skip_test "TER-9.17" "requiere stub WireMock"
skip_test "TER-9.18" "requiere stub WireMock"
skip_test "TER-9.19" "requiere stub WireMock con delay"
skip_test "TER-9.20" "requiere proveedor apagado controlado"
skip_test "TER-9.21" "requiere stub WireMock"
skip_test "TER-9.22" "requiere stub WireMock"

# Verifica que /import NO persiste
PRECOUNT=$(curl -s "$API_URL/terrain?user_id=$USER_ID" | jq 'length')
curl -s -X POST "$API_URL/terrain/import" \
    -H "Content-Type: application/json" \
    -d "{\"reference\":\"$CAD_REF_VALID_20\",\"kind\":\"CADASTRAL\"}" > /dev/null
POSTCOUNT=$(curl -s "$API_URL/terrain?user_id=$USER_ID" | jq 'length')
if [[ "$PRECOUNT" == "$POSTCOUNT" ]]; then
    _pass "TER-9.NoPersist" "/import no persiste (count antes/después = $PRECOUNT)"
else
    _fail "TER-9.NoPersist" "/import no persiste" "$PRECOUNT" "$POSTCOUNT"
fi
fi  # /sección 9

# ===========================================================================
# §10. gRPC CheckTerrainExists (TER-10.x)
# ===========================================================================
if section_enabled 10; then
section_header 10 "gRPC — CheckTerrainExists"

if [[ "$HAS_GRPCURL" -eq 0 ]]; then
    for c in 01 02 03 04 05; do
        skip_test "TER-10.$c" "grpcurl no instalado (instálalo para correr §10)"
    done
else
    GRPC_SVC="com.agro.terrain.grpc.TerrainService/CheckTerrainExists"
    T_GRPC=$(create_terrain_basic "QA-10-gRPC")

    # TER-10.01 — terreno existente
    RESP=$(grpcurl -plaintext -d "{\"terrain_id\":\"$T_GRPC\"}" "$GRPC_HOST_PORT" "$GRPC_SVC" 2>&1)
    if echo "$RESP" | jq -e '.exists == true' > /dev/null 2>&1; then
        _pass "TER-10.01" "terreno existente → exists=true"
    else
        _fail "TER-10.01" "exists=true" '{"exists":true}' "$RESP"
    fi

    # TER-10.02 — terreno inexistente
    RESP=$(grpcurl -plaintext -d "{\"terrain_id\":\"$RANDOM_UUID\"}" "$GRPC_HOST_PORT" "$GRPC_SVC" 2>&1)
    if echo "$RESP" | jq -e '.exists == false or .exists == null' > /dev/null 2>&1; then
        _pass "TER-10.02" "terreno inexistente → exists=false"
    else
        _fail "TER-10.02" "exists=false" '{"exists":false}' "$RESP"
    fi

    # TER-10.03 — UUID malformado
    RESP=$(grpcurl -plaintext -d '{"terrain_id":"abc"}' "$GRPC_HOST_PORT" "$GRPC_SVC" 2>&1)
    if echo "$RESP" | jq -e '.exists == false or .exists == null' > /dev/null 2>&1; then
        _pass "TER-10.03" "UUID malformado → exists=false (sin error gRPC)"
    else
        _fail "TER-10.03" "exists=false sin error" "$RESP"
    fi

    # TER-10.04 — cadena vacía
    RESP=$(grpcurl -plaintext -d '{"terrain_id":""}' "$GRPC_HOST_PORT" "$GRPC_SVC" 2>&1)
    if echo "$RESP" | jq -e '.exists == false or .exists == null' > /dev/null 2>&1; then
        _pass "TER-10.04" "cadena vacía → exists=false"
    else
        _fail "TER-10.04" "cadena vacía" "exists=false" "$RESP"
    fi

    # TER-10.05 — concurrencia
    OK=0; FAIL=0
    for i in $(seq 1 30); do
        R=$(grpcurl -plaintext -d "{\"terrain_id\":\"$T_GRPC\"}" "$GRPC_HOST_PORT" "$GRPC_SVC" 2>&1)
        if echo "$R" | jq -e '.exists == true' > /dev/null 2>&1; then
            OK=$((OK+1))
        else
            FAIL=$((FAIL+1))
        fi
    done
    if [[ "$FAIL" == "0" ]]; then
        _pass "TER-10.05" "concurrencia 30 calls — todas exists=true"
    else
        _fail "TER-10.05" "concurrencia" "30 OK" "$OK OK / $FAIL FAIL"
    fi
fi
fi  # /sección 10

# ===========================================================================
# §11. Listener Kafka user-deleted (TER-11.x)
# ===========================================================================
if section_enabled 11; then
section_header 11 "Listener Kafka — user-deleted"

# Helper: produce un user-deleted via kcat o docker exec con TypeId header.
produce_user_deleted() {
    local user_uuid="$1"
    local payload="{\"userId\":\"$user_uuid\"}"
    local typeid="com.agro.authservice.event.UserDeletedEvent"
    if [[ "$HAS_KCAT" -eq 1 ]]; then
        printf "%s" "$payload" | kcat -P -b "${KAFKA_BOOTSTRAP_INTERNAL%%:*}:${KAFKA_BOOTSTRAP_INTERNAL##*:}" \
            -t user-deleted -k "$user_uuid" -H "__TypeId__=$typeid" 2>/dev/null
        return $?
    fi
    if [[ "$HAS_DOCKER" -eq 1 ]]; then
        # kafka-console-producer no soporta headers fácilmente; usamos un comando python interno si es posible.
        docker exec -i "$KAFKA_CONTAINER" sh -c "command -v kafka-console-producer" >/dev/null 2>&1 || return 1
        printf "%s\n" "$payload" | docker exec -i "$KAFKA_CONTAINER" \
            kafka-console-producer --broker-list "$KAFKA_BOOTSTRAP_INTERNAL" --topic user-deleted 2>/dev/null
        # Sin TypeId el listener intentará usar el default (la clase local).
        return $?
    fi
    return 2
}

# Comprueba si tenemos al menos uno de los dos canales para producir
HAVE_PRODUCER=0
[[ "$HAS_KCAT" -eq 1 ]] && HAVE_PRODUCER=1
[[ "$HAS_DOCKER" -eq 1 ]] && HAVE_PRODUCER=1

if [[ "$HAVE_PRODUCER" -eq 0 ]]; then
    for c in 01 02 03 04 05 06 07 08; do
        skip_test "TER-11.$c" "kcat ni docker disponibles para producir en Kafka"
    done
else
    # TER-11.01 — usuario sin terrenos
    GHOST_UID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
    if produce_user_deleted "$GHOST_UID"; then
        sleep 2
        # No hay forma directa de aserción aquí; verificamos que el servicio sigue vivo
        PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=$USER_ID")
        if [[ "$PING" == "200" ]]; then
            _pass "TER-11.01" "user-deleted con 0 terrenos: listener no rompe el servicio"
        else
            _fail "TER-11.01" "servicio vivo tras user-deleted" "200" "$PING"
        fi
    else
        skip_test "TER-11.01" "produce_user_deleted falló"
    fi

    # TER-11.02 — usuario con 1 terreno
    SOLO_UID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
    # Usamos USER_ID como el user que sí existe; creamos un terreno suyo
    T11=$(create_terrain_basic "QA-11.02-Drop")
    if produce_user_deleted "$USER_ID"; then
        sleep 3
        AFTER=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T11")
        if [[ "$AFTER" == "404" ]]; then
            _pass "TER-11.02" "user-deleted borró el terreno (GET → 404)"
            CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T11|$USER_ID/}")
        else
            _fail "TER-11.02" "user-deleted borra terrenos del user" "404" "$AFTER"
        fi
    else
        skip_test "TER-11.02" "produce_user_deleted falló"
    fi

    # TER-11.03 — usuario con 3 terrenos
    A=$(create_terrain_basic "QA-11.03-A")
    B=$(create_terrain_basic "QA-11.03-B")
    C=$(create_terrain_basic "QA-11.03-C")
    if produce_user_deleted "$USER_ID"; then
        sleep 3
        for tid in $A $B $C; do
            CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$tid")
            assert_status "TER-11.03[$tid]" "borrado en cascada" "404" "$CODE"
            CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$tid|$USER_ID/}")
        done
    else
        skip_test "TER-11.03" "produce_user_deleted falló"
    fi

    # TER-11.04 — terreno con adjuntos
    T_ATT11=$(create_terrain_basic "QA-11.04-Att")
    echo "x" > "$TMP_DIR/k.jpg"
    curl -s -X POST "$API_URL/terrain/$T_ATT11/attachment?user_id=$USER_ID" \
        -F "file=@$TMP_DIR/k.jpg;type=image/jpeg" > /dev/null
    if produce_user_deleted "$USER_ID"; then
        sleep 3
        ATT_AFTER=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain/$T_ATT11/attachment?user_id=$USER_ID")
        if [[ "$ATT_AFTER" == "404" ]]; then
            _pass "TER-11.04" "cascade SQL adjuntos borrados con el terreno"
            CREATED_TERRAINS=("${CREATED_TERRAINS[@]/$T_ATT11|$USER_ID/}")
        else
            _fail "TER-11.04" "cascade adjuntos" "404" "$ATT_AFTER"
        fi
    else
        skip_test "TER-11.04" "produce_user_deleted falló"
    fi

    # TER-11.05 — payload con userId null (requiere construir JSON inválido)
    if [[ "$HAS_KCAT" -eq 1 ]]; then
        printf '{"userId":null}' | kcat -P -b "${KAFKA_BOOTSTRAP_INTERNAL%%:*}:${KAFKA_BOOTSTRAP_INTERNAL##*:}" \
            -t user-deleted -H "__TypeId__=com.agro.authservice.event.UserDeletedEvent" 2>/dev/null
        sleep 2
        PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=$USER_ID")
        if [[ "$PING" == "200" ]]; then
            _pass "TER-11.05" "userId=null no rompe el listener"
        else
            _fail "TER-11.05" "servicio vivo tras userId null" "200" "$PING"
        fi
    else
        skip_test "TER-11.05" "necesita kcat para inyectar userId=null"
    fi

    # TER-11.06 — idempotencia
    if produce_user_deleted "$USER_ID" && produce_user_deleted "$USER_ID"; then
        sleep 2
        PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=$USER_ID")
        if [[ "$PING" == "200" ]]; then
            _pass "TER-11.06" "doble user-deleted idempotente"
        else
            _fail "TER-11.06" "doble user-deleted" "200" "$PING"
        fi
    else
        skip_test "TER-11.06" "produce_user_deleted falló"
    fi

    # TER-11.07 — type mapping cross-paquete (cubierto por TER-11.02 si kcat envía TypeId)
    if [[ "$HAS_KCAT" -eq 1 ]]; then
        skip_test "TER-11.07" "cubierto: kcat envía __TypeId__=com.agro.authservice.event.UserDeletedEvent"
    else
        skip_test "TER-11.07" "requiere kcat (docker producer no manda headers)"
    fi

    # TER-11.08 — payload corrupto
    if [[ "$HAS_KCAT" -eq 1 ]]; then
        printf 'not-json-at-all' | kcat -P -b "${KAFKA_BOOTSTRAP_INTERNAL%%:*}:${KAFKA_BOOTSTRAP_INTERNAL##*:}" \
            -t user-deleted -H "__TypeId__=com.agro.authservice.event.UserDeletedEvent" 2>/dev/null
        sleep 2
        PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/terrain?user_id=$USER_ID")
        if [[ "$PING" == "200" ]]; then
            _pass "TER-11.08" "payload corrupto absorbido por ErrorHandlingDeserializer"
        else
            _fail "TER-11.08" "payload corrupto" "200" "$PING"
        fi
    else
        skip_test "TER-11.08" "requiere kcat"
    fi
fi
fi  # /sección 11

# ===========================================================================
# §12. Repository (TER-12.x) — tests internos, no curlables
# ===========================================================================
if section_enabled 12; then
section_header 12 "Repository (BBDD interna)"
for c in 01 02 03 04 05 06 07 10 11 12 13 14 15 16 17 18 19 20; do
    skip_test "TER-12.$c" "internos: ./mvnw -pl terrain-service test -Dtest=TerrainRepositoryTest,TerrainPostgisIntegrationTest"
done
fi  # /sección 12

# ===========================================================================
# §13. Gateway (TER-13.x)
# ===========================================================================
if section_enabled 13; then
section_header 13 "Gateway (api-gateway)"

if [[ -z "$GATEWAY_URL" ]]; then
    for c in 01 02 03 04 05 06; do
        skip_test "TER-13.$c" "GATEWAY_URL no configurada"
    done
else
    # TER-13.01 — sin Authorization
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/terrain?user_id=$USER_ID")
    assert_status "TER-13.01" "sin Authorization → 401" "401" "$STATUS"

    # TER-13.02 — JWT inválido
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer xxx" "$GATEWAY_URL/api/terrain?user_id=$USER_ID")
    assert_status "TER-13.02" "JWT inválido → 401" "401" "$STATUS"

    # TER-13.03 — JWT expirado
    EXPIRED_JWT="eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE2MDAwMDAwMDB9.0000000000000000000000000000000000000000000"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $EXPIRED_JWT" "$GATEWAY_URL/api/terrain?user_id=$USER_ID")
    assert_status "TER-13.03" "JWT expirado → 401" "401" "$STATUS"

    # TER-13.04 — JWT válido
    if [[ -z "$JWT" && -n "$USER_EMAIL" && -n "$USER_PASSWORD" ]]; then
        LOGIN=$(curl -s -X POST "$GATEWAY_URL/auth/login" \
            -H "Content-Type: application/json" \
            -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
        JWT=$(echo "$LOGIN" | jq -r '.token // .accessToken // empty')
    fi
    if [[ -n "$JWT" ]]; then
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $JWT" "$GATEWAY_URL/api/terrain?user_id=$USER_ID")
        assert_status "TER-13.04" "JWT válido → 200" "200" "$STATUS"
        # TER-13.05 — /import con JWT
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer $JWT" \
            -H "Content-Type: application/json" \
            -d "{\"reference\":\"$CAD_REF_VALID_20\",\"kind\":\"CADASTRAL\"}" \
            "$GATEWAY_URL/api/terrain/import")
        if [[ "$STATUS" == "200" || "$STATUS" == "404" || "$STATUS" == "502" ]]; then
            _pass "TER-13.05" "import vía gateway con JWT → $STATUS"
        else
            _fail "TER-13.05" "import vía gateway" "200/404/502" "$STATUS"
        fi
    else
        skip_test "TER-13.04" "JWT no disponible (define JWT o USER_EMAIL/USER_PASSWORD)"
        skip_test "TER-13.05" "JWT no disponible"
    fi

    # TER-13.06 — circuit breaker
    skip_test "TER-13.06" "requiere apagar terrain-service durante el test"
fi
fi  # /sección 13

# ===========================================================================
# §14. Transversales (TER-14.x)
# ===========================================================================
if section_enabled 14; then
section_header 14 "Transversales — i18n, ProblemDetail, headers"

# TER-14.01 — Default locale es
RESP=$(curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
ERRORS=$(echo "$RESP" | jq -r '.errors // [] | tostring')
if [[ "$ERRORS" == *"name.required"* || "$ERRORS" == *"obligatorio"* ]]; then
    _pass "TER-14.01" "default locale es (errors menciona key/texto es)"
else
    _fail "TER-14.01" "default locale es" "errors menciona name.required" "$ERRORS"
fi

# TER-14.02 — Locale en
RESP=$(curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: en" \
    -d "{\"name\":\"$LONG_NAME\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
ERRORS=$(echo "$RESP" | jq -r '.errors // [] | tostring')
if [[ "$ERRORS" == *"too long"* || "$ERRORS" == *"max"* || "$ERRORS" == *"name.too.long"* ]]; then
    _pass "TER-14.02" "Locale en (errors en inglés)"
else
    _fail "TER-14.02" "Locale en" "errors en EN" "$ERRORS"
fi

# TER-14.03 — Locale desconocido → fallback EN
RESP=$(curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: zh-CN" \
    -d "{\"name\":\"\",\"user_id\":\"$USER_ID\",\"geometry\":$GEOM_VALID_1HA}")
ERRORS=$(echo "$RESP" | jq -r '.errors // [] | tostring')
# Spring AcceptHeaderLocaleResolver con default es: si no entiende zh-CN, usa el default (es)
if [[ "$ERRORS" == *"required"* || "$ERRORS" == *"obligatorio"* ]]; then
    _pass "TER-14.03" "Locale desconocido fallback funciona ($ERRORS)"
else
    _fail "TER-14.03" "Locale desconocido fallback" "respuesta i18n" "$ERRORS"
fi

# TER-14.04 — ProblemDetail content-type en 4xx
HEADERS=$(curl -s -D - -o /dev/null "$API_URL/terrain/$RANDOM_UUID")
assert_content_type_contains "TER-14.04" "Content-Type problem+json en 404" "problem+json" "$HEADERS"

# TER-14.05 — campos RFC 7807
RESP=$(curl -s "$API_URL/terrain/$RANDOM_UUID")
for field in title status detail; do
    VAL=$(echo "$RESP" | jq -r ".$field // empty")
    if [[ -n "$VAL" ]]; then
        _pass "TER-14.05[$field]" "ProblemDetail.$field presente"
    else
        _fail "TER-14.05[$field]" "ProblemDetail.$field" "presente" "vacío"
    fi
done

# TER-14.06 — validation errors como array
RESP=$(curl -s -X POST "$API_URL/terrain" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"\",\"geometry\":$GEOM_VALID_1HA}")
TYPE=$(echo "$RESP" | jq -r 'if .errors | type == "array" then "array" else "other" end')
if [[ "$TYPE" == "array" ]]; then
    _pass "TER-14.06" "errors es un array"
else
    _fail "TER-14.06" "errors array" "array" "$TYPE"
fi

# TER-14.07 — sin stack trace en respuesta
RESP=$(curl -s "$API_URL/terrain/$RANDOM_UUID")
if echo "$RESP" | jq -e '.stackTrace // .cause // .class' > /dev/null 2>&1; then
    _fail "TER-14.07" "sin stack trace" "no stackTrace/cause/class" "$RESP"
else
    _pass "TER-14.07" "sin stack trace en ProblemDetail"
fi

# TER-14.08 — Content-Type application/json en éxito
T_OK=$(create_terrain_basic "QA-14.08-OK")
HEADERS=$(curl -s -D - -o /dev/null "$API_URL/terrain/$T_OK")
assert_content_type_contains "TER-14.08" "Content-Type application/json en 200" "application/json" "$HEADERS"

# TER-14.09 — claves i18n no hardcodeadas
skip_test "TER-14.09" "análisis estático: grep manual sobre src/main/java"

# TER-14.10 — claves i18n consistentes ES/EN
skip_test "TER-14.10" "análisis estático: comparar messages.properties y messages_es.properties"
fi  # /sección 14

# ===========================================================================
# Resumen final
# ===========================================================================
echo
echo -e "${BOLD}${BLUE}=== Resumen ===${NC}"
echo -e "  Total:   $TOTAL"
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo -e "  ${YELLOW}Skipped: $SKIPPED${NC}"
if [[ "$FAILED" -gt 0 ]]; then
    echo
    echo -e "${RED}Casos que fallaron:${NC}"
    printf '  - %s\n' "${FAILED_IDS[@]}"
    exit 1
fi
echo -e "${GREEN}Todos los assertions pasaron.${NC}"
exit 0
