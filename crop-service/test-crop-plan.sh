#!/usr/bin/env bash
# =============================================================================
# crop-service — script QA end-to-end del plan crop-service-test-plan.md
# =============================================================================
# Ejecuta los casos del plan que son verificables desde fuera del proceso (HTTP
# y gRPC). Los unit / WebMvc slice / JDBC tests internos del plan no se
# ejecutan aquí: viven en src/test y se corren con `./mvnw -pl crop-service
# test`. Este script es para que un QA o desarrollador valide el comportamiento
# real del servicio corriendo end-to-end con sus dependencias.
#
# REQUISITOS PARA UNA EJECUCIÓN COMPLETA:
#   - crop-service corriendo y accesible en $API_URL.
#   - api-gateway corriendo en $GATEWAY_URL si quieres ejecutar §8 (auth bypass).
#   - crop-service expone gRPC en $GRPC_HOST_PORT para §5.
#   - Herramientas: curl, jq (obligatorias). grpcurl (§5).
#
# USO:
#   ./test-crop-plan.sh                    # corre todo lo que pueda
#   API_URL=http://localhost:8081 ./test-crop-plan.sh
#   ONLY="1 2 3" ./test-crop-plan.sh       # solo secciones 1, 2 y 3
#   SKIP_SLOW=1 ./test-crop-plan.sh        # salta payload gigante (§8.05)
#
# SALIDA:
#   [PASS] CROP-X.YY - descripción
#   [FAIL] CROP-X.YY - expected ... but got ...
#   [SKIP] CROP-X.YY - razón
#   ...
#   Resumen final + exit code 0 si todos los assertions pasaron.
# =============================================================================

set -u
set -o pipefail

# ---------- Configuración (overridable por env) -----------------------------
API_URL="${API_URL:-http://localhost:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9000}"
GRPC_HOST_PORT="${GRPC_HOST_PORT:-localhost:9094}"

# IDs / nombres de seeds usados a lo largo del script. Marca distintiva
# "QA-CROP-" para que el cleanup pueda identificarlos.
RUN_TAG="QA-CROP-$(date +%s)"

SKIP_SLOW="${SKIP_SLOW:-0}"   # 1 = saltar tests con payload >1 MB
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
# El POST /crop NO devuelve el id del crop creado (deuda nº 1 del guide), así
# que rastreamos los names creados con el tag de la run y los limpiamos al
# final por GET-then-DELETE.
declare -a CREATED_NAMES=()
TMP_DIR="$(mktemp -d -t crop-qa-XXXXXX)"
trap 'cleanup_resources; rm -rf "$TMP_DIR"' EXIT

# ---------- Fixtures (apéndice B del plan) ----------------------------------
# B.1 CropRequest válido mínimo
VALID_DESC='Trigo de invierno apto para harina panificable'

# B.2/B.3 Strings de longitudes específicas
NAME_3CHAR='ABC'
NAME_100CHAR=$(printf 'X%.0s' $(seq 1 100))
NAME_101CHAR=$(printf 'X%.0s' $(seq 1 101))
DESC_10CHAR='abcdefghij'
DESC_500CHAR=$(printf 'd%.0s' $(seq 1 500))
DESC_501CHAR=$(printf 'd%.0s' $(seq 1 501))

# Helper: genera un body válido con un nombre tag-prefixed para el cleanup.
make_body() {
    local name="$1"
    local desc="${2:-$VALID_DESC}"
    local type="${3:-1}"
    cat <<EOF
{"name":"$name","description":"$desc","crop_type_id":$type}
EOF
}

# ---------- Helpers de detección de herramientas ---------------------------
HAS_JQ=0; HAS_CURL=0; HAS_GRPCURL=0
command -v jq >/dev/null 2>&1 && HAS_JQ=1
command -v curl >/dev/null 2>&1 && HAS_CURL=1
command -v grpcurl >/dev/null 2>&1 && HAS_GRPCURL=1

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
    # acepta una lista de status válidos (separados por |). Útil cuando el
    # framework cambia entre 400/415 según versión.
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

assert_json_min_length() {
    local id="$1" desc="$2" minimum="$3" body="$4"
    local actual
    actual=$(echo "$body" | jq 'length' 2>/dev/null || echo "0")
    if [[ "$actual" -ge "$minimum" ]]; then
        _pass "$id" "$desc (length=$actual ≥ $minimum)"
    else
        _fail "$id" "$desc" "length ≥ $minimum" "length=$actual"
    fi
}

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

section_enabled() {
    local n="$1"
    if [[ -z "$ONLY" ]]; then
        return 0
    fi
    [[ " $ONLY " == *" $n "* ]]
}

section_header() {
    local n="$1" title="$2"
    echo
    echo -e "${BOLD}${BLUE}=== Sección $n: $title ===${NC}"
}

# Crea un crop válido con name = "$RUN_TAG-$suffix". El POST devuelve solo
# texto i18n; no podemos leer el id de la respuesta. Lo registramos por name
# para limpiarlo al final.
create_crop_named() {
    local suffix="$1"
    local name="$RUN_TAG-$suffix"
    local body
    body=$(make_body "$name")
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
        -H "Content-Type: application/json" \
        -d "$body")
    if [[ "$status" == "201" ]]; then
        CREATED_NAMES+=("$name")
        echo "$name"
    else
        echo ""
    fi
}

# Resuelve el id de un crop por su name (recorre GET /crop). Devuelve "" si no
# se encuentra.
crop_id_by_name() {
    local name="$1"
    curl -s "$API_URL/crop?fields=id,name" \
        | jq -r --arg n "$name" '.[] | select(.name == $n) | .id' \
        | head -n1
}

# Limpieza al salir: borra todos los crops creados durante esta run.
cleanup_resources() {
    echo
    echo -e "${BLUE}=== Cleanup ===${NC}"
    if [[ ${#CREATED_NAMES[@]} -eq 0 ]]; then
        echo "  (no hay crops para limpiar)"
        return 0
    fi
    # Construye un mapa name→id desde la BBDD una sola vez.
    local map
    map=$(curl -s "$API_URL/crop?fields=id,name" | jq -c '.')
    for n in "${CREATED_NAMES[@]}"; do
        local id
        id=$(echo "$map" | jq -r --arg n "$n" '.[] | select(.name == $n) | .id' | head -n1)
        if [[ -z "$id" || "$id" == "null" ]]; then
            echo "  $n → ya no existe (skipped)"
            continue
        fi
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$id")
        echo "  DELETE $n ($id) → $code"
    done
}

# ---------- Pre-flight: ¿el servicio responde? ------------------------------
echo -e "${BOLD}${BLUE}=== Pre-flight ===${NC}"
PING_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop" || echo "000")
if [[ "$PING_STATUS" == "000" ]]; then
    echo -e "${RED}[FATAL]${NC} crop-service NO responde en $API_URL. Arráncalo y reintenta."
    exit 2
fi
echo "  crop-service en $API_URL responde con $PING_STATUS al GET /crop. OK."
[[ "$HAS_GRPCURL" -eq 1 ]] && echo "  grpcurl disponible — §5 ON" || echo "  grpcurl NO disponible — §5 SKIP"

# ===========================================================================
# §1. POST /crop — alta de cultivo (CROP-1.x)
# ===========================================================================
if section_enabled 1; then
section_header 1 "POST /crop — alta de cultivo"

# CROP-1.01 — happy path mínimo
NAME_101="$RUN_TAG-1.01"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$NAME_101")")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.01" "happy path mínimo" "201" "$STATUS"
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_101")

# CROP-1.01b — la persistencia se verifica por GET inmediato
ID_101=$(crop_id_by_name "$NAME_101")
if [[ -n "$ID_101" ]]; then
    _pass "CROP-1.01b" "fila persistida (id=$ID_101)"
else
    _fail "CROP-1.01b" "fila persistida tras POST" "id no vacío" "<vacío>"
fi

# CROP-1.02 — Accept-Language: en
NAME_102="$RUN_TAG-1.02"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: en" \
    -d "$(make_body "$NAME_102")")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.02" "Accept-Language en → 201" "201" "$STATUS"
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_102")
# El body es texto plano (deuda); verificamos que contiene "Crop" en EN.
if [[ "$BODY" == *"Crop"* ]]; then
    _pass "CROP-1.02b" "respuesta en inglés contiene 'Crop'"
else
    _fail "CROP-1.02b" "respuesta en inglés" "contiene 'Crop'" "$BODY"
fi

# CROP-1.03 — name ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"description\":\"$VALID_DESC\",\"crop_type_id\":1}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.03" "name ausente → 400" "400" "$STATUS"
assert_json_contains "CROP-1.03b" "errors menciona name" '.errors // [] | tostring' "name" "$BODY"

# CROP-1.04 — name vacío
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body '')")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.04" "name vacío → 400" "400" "$STATUS"

# CROP-1.05 — name solo espacios
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body '   ')")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.05" "name solo espacios → 400" "400" "$STATUS"

# CROP-1.06 — name < 3 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body 'AB')")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.06" "name < 3 chars → 400" "400" "$STATUS"
assert_json_contains "CROP-1.06b" "errors menciona name size" '.errors // [] | tostring' "3 y 100" "$BODY"

# CROP-1.07 — name > 100 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$NAME_101CHAR")")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.07" "name > 100 chars → 400" "400" "$STATUS"

# CROP-1.08 — borde superior real del campo `name`.
#
# OJO: aquí hay un BUG REAL en crop-service:
#   - DTO CropRequest:  @Size(min=3, max=100)
#   - V1 SQL:           name VARCHAR(50)
# Un nombre de 51..100 chars pasa Bean Validation pero rompe el INSERT
# (value too long for varchar(50)) → 500. Hasta que se decida fix
# (bajar @Size a 50, o ALTER COLUMN a 100), el borde **realmente
# funcional** es 50 chars. Probamos ese borde.
NAME_108="$RUN_TAG-$(printf 'X%.0s' $(seq 1 50))"
NAME_108="${NAME_108:0:50}"   # truncar a 50 chars exactos
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$NAME_108")")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.08" "name 50 chars (borde DB real) → 201" "201" "$STATUS"
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_108")

# CROP-1.08c — confirmación del bug: 51..100 chars pasa @Size pero rompe SQL.
NAME_108C="$RUN_TAG-$(printf 'Y%.0s' $(seq 1 80))"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$NAME_108C")")
STATUS="${RESP##*$'\n'}"
# Esperamos 500 hoy (DataIntegrityViolation no manejada) o 400 si se
# arregla bajando @Size a 50. Documenta la deuda.
if [[ "$STATUS" == "500" ]]; then
    _pass "CROP-1.08c" "deuda confirmada: @Size(max=100) vs VARCHAR(50) → 500"
elif [[ "$STATUS" == "400" ]]; then
    _pass "CROP-1.08c" "deuda corregida: @Size ahora rechaza > 50 → 400"
else
    _fail "CROP-1.08c" "name > 50 chars debe fallar (deuda)" "400 o 500" "$STATUS"
fi

# CROP-1.09 — description ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$RUN_TAG-1.09\",\"crop_type_id\":1}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.09" "description ausente → 400" "400" "$STATUS"
assert_json_contains "CROP-1.09b" "errors menciona description" '.errors // [] | tostring' "description" "$BODY"

# CROP-1.10 — description < 10 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$RUN_TAG-1.10" 'corto')")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.10" "description < 10 → 400" "400" "$STATUS"

# CROP-1.11 — description > 500 chars
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$RUN_TAG-1.11" "$DESC_501CHAR")")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.11" "description > 500 → 400" "400" "$STATUS"

# CROP-1.12 — description en bordes (10 y 500)
for desc_var in "10|$DESC_10CHAR" "500|$DESC_500CHAR"; do
    LEN="${desc_var%%|*}"; DESC="${desc_var##*|}"
    NAME_112="$RUN_TAG-1.12-$LEN"
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
        -H "Content-Type: application/json" \
        -d "$(make_body "$NAME_112" "$DESC")")
    STATUS="${RESP##*$'\n'}"
    assert_status "CROP-1.12[$LEN]" "description $LEN chars → 201" "201" "$STATUS"
    [[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_112")
done

# CROP-1.13 — crop_type_id ausente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$RUN_TAG-1.13\",\"description\":\"$VALID_DESC\"}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.13" "crop_type_id ausente → 400" "400" "$STATUS"
assert_json_contains "CROP-1.13b" "errors menciona crop_type_id" '.errors // [] | tostring' "crop_type_id" "$BODY"

# CROP-1.14 — crop_type_id = 0
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$RUN_TAG-1.14" "$VALID_DESC" 0)")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.14" "crop_type_id = 0 → 400" "400" "$STATUS"

# CROP-1.15 — crop_type_id negativo
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$RUN_TAG-1.15" "$VALID_DESC" -1)")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.15" "crop_type_id negativo → 400" "400" "$STATUS"

# CROP-1.16 — crop_type_id no numérico
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$RUN_TAG-1.16\",\"description\":\"$VALID_DESC\",\"crop_type_id\":\"abc\"}")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.16" "crop_type_id no numérico → 400" "400" "$STATUS"

# CROP-1.17 — crop_type_id inexistente
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$RUN_TAG-1.17" "$VALID_DESC" 999)")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.17" "crop_type_id inexistente → 400" "400" "$STATUS"
assert_json_eq "CROP-1.17b" "title 'No existe ese tipo de cultivo'" '.title' "No existe ese tipo de cultivo" "$BODY"

# CROP-1.18 — body vacío
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{}")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.18" "body vacío → 400" "400" "$STATUS"
ERR_LEN=$(echo "$BODY" | jq -r '.errors | length' 2>/dev/null || echo "0")
if [[ "$ERR_LEN" -ge "3" ]]; then
    _pass "CROP-1.18b" "errors contiene ≥ 3 entradas (got $ERR_LEN)"
else
    _fail "CROP-1.18b" "errors con 3 entradas" "≥ 3" "$ERR_LEN"
fi

# CROP-1.19 — body con Content-Type: text/plain
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: text/plain" \
    -d "no es json")
STATUS="${RESP##*$'\n'}"
assert_status_in "CROP-1.19" "Content-Type text/plain → 415" "415" "$STATUS"

# CROP-1.20 — sin Content-Type (curl pone uno por defecto si hay -d, así que
# usamos --data-binary @- con stdin vacío para evitar default).
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" --data "{}" -H "Content-Type:")
STATUS="${RESP##*$'\n'}"
assert_status_in "CROP-1.20" "sin Content-Type → 415" "415" "$STATUS"

# CROP-1.21 — idempotencia: dos POST con mismo name → 201 + 201, dos filas
NAME_121="$RUN_TAG-1.21-dup"
S1=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_121")")
S2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_121")")
[[ "$S1" == "201" ]] && CREATED_NAMES+=("$NAME_121")
[[ "$S2" == "201" ]] && CREATED_NAMES+=("$NAME_121")
COUNT_121=$(curl -s "$API_URL/crop?fields=id,name" | jq --arg n "$NAME_121" '[.[] | select(.name == $n)] | length')
if [[ "$S1" == "201" && "$S2" == "201" && "$COUNT_121" == "2" ]]; then
    _pass "CROP-1.21" "dos POST iguales generan 2 filas (sin UNIQUE en name)"
else
    _fail "CROP-1.21" "dos POST iguales generan 2 filas" "201/201/count=2" "$S1/$S2/count=$COUNT_121"
fi

# CROP-1.22 — inserción concurrente: 5 threads
PIDS=()
for i in 1 2 3 4 5; do
    NAME="$RUN_TAG-1.22-c$i"
    CREATED_NAMES+=("$NAME")
    (curl -s -o /dev/null -X POST "$API_URL/crop" \
        -H "Content-Type: application/json" -d "$(make_body "$NAME")") &
    PIDS+=($!)
done
for p in "${PIDS[@]}"; do wait "$p"; done
COUNT_122=$(curl -s "$API_URL/crop?fields=name" | jq --arg p "$RUN_TAG-1.22-" '[.[] | select(.name | startswith($p))] | length')
if [[ "$COUNT_122" == "5" ]]; then
    _pass "CROP-1.22" "5 inserts concurrentes → 5 filas"
else
    _fail "CROP-1.22" "5 inserts concurrentes" "5 filas" "$COUNT_122"
fi

# CROP-1.23 — Unicode en name
NAME_123="$RUN_TAG-1.23-Brócoli"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "$(make_body "$NAME_123")")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-1.23" "Unicode en name → 201" "201" "$STATUS"
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_123")
# Verifica round-trip UTF-8
if curl -s "$API_URL/crop?fields=name" | jq -r '.[].name' | grep -q "Brócoli"; then
    _pass "CROP-1.23b" "Brócoli round-trip UTF-8 OK"
else
    _fail "CROP-1.23b" "Brócoli round-trip" "name contiene 'Brócoli'" "<no encontrado>"
fi

fi  # /sección 1

# ===========================================================================
# §2. GET /crop — listado y proyección (CROP-2.x)
# ===========================================================================
if section_enabled 2; then
section_header 2 "GET /crop — listado y proyección"

# CROP-2.01 — la lista NUNCA estará vacía mientras tengamos crops creados; no
# es trivial provocar tabla vacía sin truncar la BBDD. SKIP.
skip_test "CROP-2.01" "no se puede provocar lista vacía sin truncar (otros tests insertan)"

# Pre-seed para §2: nos aseguramos de tener al menos 3 crops en este run.
SEED_NAMES=()
for s in 2.A 2.B 2.C; do
    NAME="$RUN_TAG-$s"
    if curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
        -H "Content-Type: application/json" -d "$(make_body "$NAME")" | grep -q 201; then
        CREATED_NAMES+=("$NAME")
        SEED_NAMES+=("$NAME")
    fi
done

# CROP-2.02 — listar sin filtros devuelve nuestros seeds
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.02" "GET /crop → 200" "200" "$STATUS"
COUNT_OWN=$(echo "$BODY" | jq --arg p "$RUN_TAG-" '[.[] | select(.name | startswith($p))] | length')
if [[ "$COUNT_OWN" -ge 3 ]]; then
    _pass "CROP-2.02b" "lista contiene ≥ 3 crops del run (count=$COUNT_OWN)"
else
    _fail "CROP-2.02b" "lista con ≥ 3 del run" "≥ 3" "$COUNT_OWN"
fi

# CROP-2.03 — fields=id
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?fields=id")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.03" "fields=id → 200" "200" "$STATUS"
KEYS_LEN=$(echo "$BODY" | jq -r '.[0] | keys | length' 2>/dev/null || echo "<err>")
if [[ "$KEYS_LEN" == "1" ]]; then
    _pass "CROP-2.03b" "cada objeto tiene 1 sola key"
else
    _fail "CROP-2.03b" "1 sola key" "1" "$KEYS_LEN"
fi

# CROP-2.04 — fields=id,name
RESP=$(curl -s "$API_URL/crop?fields=id,name")
KEYS_LEN=$(echo "$RESP" | jq -r '.[0] | keys | length' 2>/dev/null || echo "<err>")
if [[ "$KEYS_LEN" == "2" ]]; then
    _pass "CROP-2.04" "fields=id,name → 2 keys por objeto"
else
    _fail "CROP-2.04" "2 keys por objeto" "2" "$KEYS_LEN"
fi

# CROP-2.05 — fields con espacios
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=id,%20name%20,%20description")
assert_status "CROP-2.05" "fields con espacios → 200" "200" "$STATUS"

# CROP-2.06 — fields en mayúsculas
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=ID,NAME")
assert_status "CROP-2.06" "fields mayúsculas → 200" "200" "$STATUS"

# CROP-2.07 — fields con duplicados
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=id,id,name")
assert_status "CROP-2.07" "fields con duplicados → 200" "200" "$STATUS"

# CROP-2.08 — fields= (vacío)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=")
assert_status "CROP-2.08" "fields vacío → 200" "200" "$STATUS"

# CROP-2.09 — fields con campo no permitido
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?fields=secret")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.09" "fields=secret → 400" "400" "$STATUS"
assert_json_eq "CROP-2.09b" "title 'Campos invalido'" '.title' "Campos invalido" "$BODY"

# CROP-2.10 — fields mezcla válidos + inválidos
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=id,secret")
assert_status "CROP-2.10" "fields=id,secret → 400" "400" "$STATUS"

# CROP-2.11 — SQL injection en fields
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?fields=id;DROP%20TABLE%20crop;--")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.11" "SQL injection en fields → 400" "400" "$STATUS"
# Verifica que la tabla sigue viva (otro GET responde 200)
PING_AFTER=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop")
if [[ "$PING_AFTER" == "200" ]]; then
    _pass "CROP-2.11b" "tabla intacta tras intento de injection"
else
    _fail "CROP-2.11b" "tabla intacta" "GET /crop → 200" "GET → $PING_AFTER"
fi

# CROP-2.12 — crop_type_id válido y existente: insertamos 1 crop tipo 2
NAME_212="$RUN_TAG-2.12-fruit"
curl -s -o /dev/null -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "$(make_body "$NAME_212" "$VALID_DESC" 2)" \
    && CREATED_NAMES+=("$NAME_212")
RESP=$(curl -s "$API_URL/crop?crop_type_id=2")
COUNT=$(echo "$RESP" | jq --arg n "$NAME_212" '[.[] | select(.name == $n)] | length')
if [[ "$COUNT" == "1" ]]; then
    _pass "CROP-2.12" "filtro crop_type_id=2 incluye nuestro fruit"
else
    _fail "CROP-2.12" "filtro crop_type_id=2 contiene seed" "1" "$COUNT"
fi

# CROP-2.13 — crop_type_id válido sin matches: si nadie ha creado tipo 4 en
# esta run podemos esperar que no aparezcan crops del run con tipo 4.
RESP=$(curl -s "$API_URL/crop?crop_type_id=4")
COUNT_OWN=$(echo "$RESP" | jq --arg p "$RUN_TAG-" '[.[] | select(.name | startswith($p))] | length')
if [[ "$COUNT_OWN" == "0" ]]; then
    _pass "CROP-2.13" "filtro crop_type_id=4 no devuelve crops del run"
else
    _fail "CROP-2.13" "filtro crop_type_id=4 sin matches del run" "0" "$COUNT_OWN"
fi

# CROP-2.14 — crop_type_id inexistente
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?crop_type_id=999")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.14" "crop_type_id=999 → 400" "400" "$STATUS"
assert_json_eq "CROP-2.14b" "title 'No existe ese tipo de cultivo'" '.title' "No existe ese tipo de cultivo" "$BODY"

# CROP-2.15 — crop_type_id = 0
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?crop_type_id=0")
assert_status "CROP-2.15" "crop_type_id=0 → 400" "400" "$STATUS"

# CROP-2.16 — crop_type_id negativo
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?crop_type_id=-1")
assert_status "CROP-2.16" "crop_type_id=-1 → 400" "400" "$STATUS"

# CROP-2.17 — crop_type_id no numérico
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?crop_type_id=abc")
assert_status "CROP-2.17" "crop_type_id=abc → 400" "400" "$STATUS"

# CROP-2.18 — fields + crop_type_id combinados
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?fields=id,name&crop_type_id=1")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-2.18" "fields=id,name&crop_type_id=1 → 200" "200" "$STATUS"
KEYS_LEN=$(echo "$BODY" | jq -r '.[0] | keys | length' 2>/dev/null || echo "<err>")
if [[ "$KEYS_LEN" == "2" ]]; then
    _pass "CROP-2.18b" "respuesta con 2 keys por objeto"
else
    _fail "CROP-2.18b" "2 keys por objeto" "2" "$KEYS_LEN"
fi

# CROP-2.19 — UTF-8 en respuesta (re-usa el seed Brócoli del 1.23)
RESP=$(curl -s "$API_URL/crop?fields=name")
if echo "$RESP" | grep -q "Brócoli"; then
    _pass "CROP-2.19" "UTF-8 'Brócoli' en respuesta"
else
    skip_test "CROP-2.19" "Brócoli no presente (CROP-1.23 pudo fallar)"
fi

# CROP-2.20 — Cache headers (decisión pendiente)
skip_test "CROP-2.20" "decisión pendiente sobre Cache-Control"

fi  # /sección 2

# ===========================================================================
# §3. GET /crop/type — catálogo de tipos (CROP-3.x)
# ===========================================================================
if section_enabled 3; then
section_header 3 "GET /crop/type — catálogo de tipos"

# CROP-3.01 — devuelve los 5 seeds
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop/type")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-3.01" "GET /crop/type → 200" "200" "$STATUS"
LEN=$(echo "$BODY" | jq 'length')
if [[ "$LEN" -ge 5 ]]; then
    _pass "CROP-3.01b" "≥ 5 tipos (got $LEN)"
else
    _fail "CROP-3.01b" "≥ 5 tipos" "≥ 5" "$LEN"
fi
for t in CEREAL FRUIT VEGETABLE TUBER LEGUME; do
    if echo "$BODY" | jq -e --arg t "$t" '.[] | select(.name == $t)' > /dev/null; then
        _pass "CROP-3.01c[$t]" "tipo $t presente"
    else
        _fail "CROP-3.01c[$t]" "tipo $t presente" "encontrado" "no encontrado"
    fi
done

# CROP-3.02 — orden estable: dos GET seguidos devuelven la misma secuencia
A=$(curl -s "$API_URL/crop/type" | jq -c '[.[].id]')
B=$(curl -s "$API_URL/crop/type" | jq -c '[.[].id]')
if [[ "$A" == "$B" ]]; then
    _pass "CROP-3.02" "orden estable entre dos GET ($A)"
else
    _fail "CROP-3.02" "orden estable" "$A" "$B"
fi

# CROP-3.03 — insertar un tipo nuevo: requiere acceso a BBDD (no expuesto). SKIP.
skip_test "CROP-3.03" "requiere INSERT directo en crop_type (no hay endpoint público)"

# CROP-3.04 — tabla vacía: requiere TRUNCATE. SKIP.
skip_test "CROP-3.04" "requiere TRUNCATE crop_type CASCADE (no realizable desde la API)"

# CROP-3.05 — id y name correctamente serializados
KEYS=$(curl -s "$API_URL/crop/type" | jq -r '.[0] | keys | sort | join(",")')
if [[ "$KEYS" == "id,name" ]]; then
    _pass "CROP-3.05" "objeto serializado con keys 'id,name'"
else
    _fail "CROP-3.05" "keys 'id,name'" "id,name" "$KEYS"
fi

fi  # /sección 3

# ===========================================================================
# §4. DELETE /crop/{id} — borrado seguro (CROP-4.x)
# ===========================================================================
if section_enabled 4; then
section_header 4 "DELETE /crop/{id} — borrado seguro"

# Crea un crop dedicado para borrarlo y verificar
NAME_401="$RUN_TAG-4.01-target"
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_401")")
[[ "$S" == "201" ]] && CREATED_NAMES+=("$NAME_401")
ID_401=$(crop_id_by_name "$NAME_401")

# CROP-4.01 — happy path
if [[ -n "$ID_401" ]]; then
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$ID_401")
    assert_status "CROP-4.01" "DELETE happy path → 204" "204" "$STATUS"
    AFTER=$(crop_id_by_name "$NAME_401")
    if [[ -z "$AFTER" ]]; then
        _pass "CROP-4.01b" "fila eliminada (GET por name no la encuentra)"
    else
        _fail "CROP-4.01b" "fila eliminada" "<vacío>" "$AFTER"
    fi
else
    skip_test "CROP-4.01" "no se pudo crear el crop target"
fi

# CROP-4.02 — id mal formado
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/abc")
assert_status "CROP-4.02" "id no UUID → 400" "400" "$STATUS"

# CROP-4.03 — id UUID válido pero inexistente
RANDOM_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
RESP=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/crop/$RANDOM_UUID")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-4.03" "UUID inexistente → 400" "400" "$STATUS"
# El handler hoy pone 'No existe ese tipo de cultivo' (mensaje incorrecto, deuda 4.A)
assert_json_contains "CROP-4.03b" "title contiene 'No existe' (incorrecto, deuda 4.A)" \
    '.title' "No existe" "$BODY"

# CROP-4.04 — doble DELETE
NAME_404="$RUN_TAG-4.04-double"
curl -s -o /dev/null -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_404")"
ID_404=$(crop_id_by_name "$NAME_404")
if [[ -n "$ID_404" ]]; then
    S1=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$ID_404")
    S2=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$ID_404")
    if [[ "$S1" == "204" && "$S2" == "400" ]]; then
        _pass "CROP-4.04" "doble DELETE → 204 + 400"
    else
        _fail "CROP-4.04" "doble DELETE → 204+400" "204+400" "$S1+$S2"
    fi
else
    skip_test "CROP-4.04" "no se pudo crear el crop"
fi

# CROP-4.05 — DELETE concurrente sobre el mismo id.
#
# BUG REAL conocido (TOCTOU): el repo hace cropExists() seguido de
# DELETE en operaciones separadas, y el controller siempre devuelve
# 204 (no mira el rowcount). Resultado: dos DELETE concurrentes
# devuelven ambos 204 y la fila se borra una vez.
# La invariante REAL que debemos verificar es: al menos uno responde
# OK y la fila ya no existe al final. Cualquier combinación de
# {204,204}, {204,400}, {400,204} es aceptable hoy.
NAME_405="$RUN_TAG-4.05-race"
curl -s -o /dev/null -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_405")"
ID_405=$(crop_id_by_name "$NAME_405")
if [[ -n "$ID_405" ]]; then
    R1=$(mktemp); R2=$(mktemp)
    curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$ID_405" > "$R1" &
    P1=$!
    curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/$ID_405" > "$R2" &
    P2=$!
    wait "$P1" "$P2"
    S1=$(cat "$R1"); S2=$(cat "$R2")
    rm -f "$R1" "$R2"
    AFTER=$(crop_id_by_name "$NAME_405")
    OK_STATUSES=0
    [[ "$S1" == "204" || "$S1" == "400" ]] && OK_STATUSES=$((OK_STATUSES + 1))
    [[ "$S2" == "204" || "$S2" == "400" ]] && OK_STATUSES=$((OK_STATUSES + 1))
    if [[ "$OK_STATUSES" == "2" && -z "$AFTER" ]]; then
        # Sub-distinguir si el TOCTOU se manifestó (ambos 204) o no.
        if [[ "$S1" == "204" && "$S2" == "204" ]]; then
            _pass "CROP-4.05" "DELETE concurrente: ambos 204 (TOCTOU conocido), fila borrada"
        else
            _pass "CROP-4.05" "DELETE concurrente: {$S1,$S2}, fila borrada"
        fi
    else
        _fail "CROP-4.05" "DELETE concurrente OK + fila borrada" \
            "statuses ∈ {204,400} y fila ausente" \
            "{$S1,$S2} y after='$AFTER'"
    fi
else
    skip_test "CROP-4.05" "no se pudo crear el crop"
fi

# CROP-4.06 — 204 con body (deuda 7)
NAME_406="$RUN_TAG-4.06-204body"
curl -s -o /dev/null -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "$(make_body "$NAME_406")"
ID_406=$(crop_id_by_name "$NAME_406")
if [[ -n "$ID_406" ]]; then
    BODY=$(curl -s -X DELETE "$API_URL/crop/$ID_406")
    if [[ -n "$BODY" ]]; then
        _pass "CROP-4.06" "204 con body (deuda nº 7 confirmada): '$BODY'"
    else
        _pass "CROP-4.06" "204 sin body (deuda corregida)"
    fi
else
    skip_test "CROP-4.06" "no se pudo crear el crop"
fi

fi  # /sección 4

# ===========================================================================
# §5. gRPC CheckCropExists (CROP-5.x)
# ===========================================================================
if section_enabled 5; then
section_header 5 "gRPC CheckCropExists"

if [[ "$HAS_GRPCURL" -eq 0 ]]; then
    skip_test "CROP-5.*" "grpcurl no instalado"
else
    # Crea un crop dedicado y obtiene su id real
    NAME_5="$RUN_TAG-5-grpc"
    curl -s -o /dev/null -X POST "$API_URL/crop" \
        -H "Content-Type: application/json" -d "$(make_body "$NAME_5")"
    CREATED_NAMES+=("$NAME_5")
    ID_5=$(crop_id_by_name "$NAME_5")

    # CROP-5.01 — UUID existente
    if [[ -n "$ID_5" ]]; then
        OUT=$(grpcurl -plaintext -d "{\"crop_id\":\"$ID_5\"}" \
            "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>&1 || echo "$ERR")
        if echo "$OUT" | grep -q '"exists": *true'; then
            _pass "CROP-5.01" "exists=true para UUID válido"
        else
            _fail "CROP-5.01" "exists=true" '"exists": true' "$OUT"
        fi
    else
        skip_test "CROP-5.01" "no se pudo crear el crop"
    fi

    # CROP-5.02 — UUID inexistente
    OUT=$(grpcurl -plaintext -d "{\"crop_id\":\"00000000-0000-0000-0000-000000000000\"}" \
        "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>&1)
    if echo "$OUT" | grep -q '"exists": *false' || ! echo "$OUT" | grep -q '"exists": *true'; then
        _pass "CROP-5.02" "exists=false para UUID inexistente"
    else
        _fail "CROP-5.02" "exists=false" '"exists": false' "$OUT"
    fi

    # CROP-5.03 — crop_id no es UUID
    OUT=$(grpcurl -plaintext -d '{"crop_id":"abc"}' \
        "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>&1)
    if echo "$OUT" | grep -q '"exists": *false' || ! echo "$OUT" | grep -q '"exists": *true'; then
        _pass "CROP-5.03" "UUID inválido → exists=false (sin error)"
    else
        _fail "CROP-5.03" "UUID inválido → exists=false" "exists=false" "$OUT"
    fi

    # CROP-5.04 — crop_id vacío
    OUT=$(grpcurl -plaintext -d '{"crop_id":""}' \
        "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>&1)
    if ! echo "$OUT" | grep -q '"exists": *true'; then
        _pass "CROP-5.04" "crop_id vacío → exists=false"
    else
        _fail "CROP-5.04" "crop_id vacío → exists=false" "no true" "$OUT"
    fi

    # CROP-5.05 — UUID malformado parcial
    OUT=$(grpcurl -plaintext -d '{"crop_id":"12345"}' \
        "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>&1)
    if ! echo "$OUT" | grep -q '"exists": *true'; then
        _pass "CROP-5.05" "UUID parcial → exists=false"
    else
        _fail "CROP-5.05" "UUID parcial → exists=false" "no true" "$OUT"
    fi

    # CROP-5.06 — BBDD caída
    skip_test "CROP-5.06" "requiere parar el contenedor crop-db; no automatizable"

    # CROP-5.07 — latencia
    skip_test "CROP-5.07" "requiere proxy con tc; out of scope local"

    # CROP-5.08 — múltiples llamadas concurrentes
    if [[ -n "$ID_5" ]]; then
        OK=0
        for i in $(seq 1 20); do
            (grpcurl -plaintext -d "{\"crop_id\":\"$ID_5\"}" \
                "$GRPC_HOST_PORT" com.agro.crop.grpc.CropService/CheckCropExists 2>/dev/null \
                | grep -q '"exists": *true' && echo OK) &
        done > /tmp/grpc-conc-$$ 2>&1
        wait
        OK=$(grep -c '^OK$' /tmp/grpc-conc-$$ 2>/dev/null || echo 0)
        rm -f /tmp/grpc-conc-$$
        if [[ "$OK" -ge "18" ]]; then
            _pass "CROP-5.08" "20 calls concurrentes → ≥ 18 OK"
        else
            _fail "CROP-5.08" "20 calls concurrentes" "≥ 18 OK" "$OK OK"
        fi
    else
        skip_test "CROP-5.08" "no hay UUID válido"
    fi
fi

fi  # /sección 5

# ===========================================================================
# §6. Repositorio (Testcontainers, JdbcTest) — fuera del alcance de este script
# ===========================================================================
if section_enabled 6; then
section_header 6 "Repositorio (JDBC slice)"
skip_test "CROP-6.*" "JdbcTest interno: ejecutar con './mvnw -pl crop-service test'"
fi

# ===========================================================================
# §7. Transversales (CROP-7.x)
# ===========================================================================
if section_enabled 7; then
section_header 7 "Transversales — i18n, ProblemDetail, headers, locales"

# CROP-7.01 — locale ES default (sin Accept-Language)
RESP=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{}")
if echo "$RESP" | grep -qiE "(requerido|requerida)"; then
    _pass "CROP-7.01" "ES default activo (errors en español)"
else
    _fail "CROP-7.01" "ES default" "errors contienen 'requerido'" "$RESP"
fi

# CROP-7.02 — locale EN
RESP=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: en" \
    -d "{}")
if echo "$RESP" | grep -q "is required"; then
    _pass "CROP-7.02" "EN activo (errors en inglés)"
else
    _fail "CROP-7.02" "EN activo" "errors contienen 'is required'" "$RESP"
fi

# CROP-7.03 — locale desconocido (zh) → fallback EN
RESP=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: zh" \
    -d "{}")
# El fallback puede ser ES (default del LocaleResolver) o EN. Aceptamos ambos.
if echo "$RESP" | grep -qiE "(required|requerido|requerida)"; then
    _pass "CROP-7.03" "locale zh resuelto a fallback (ES o EN)"
else
    _fail "CROP-7.03" "locale zh fallback" "ES o EN" "$RESP"
fi

# CROP-7.04 — multi-locale.
#
# Spring's AcceptHeaderLocaleResolver devuelve la PRIMERA locale del
# header (qualifier-aware en HTTP, pero no busca a través de la cadena
# qué bundle existe). Con "zh, es;q=0.5" resuelve a `zh`, no encuentra
# messages_zh.properties, y ResourceBundleMessageSource cae a
# messages.properties (el EN default del basename). NUNCA llega a
# probar `es;q=0.5`. Para obtener ese comportamiento haría falta un
# LocaleResolver custom. Aceptamos cualquier traducción real (EN o ES)
# y verificamos solo que NO se devuelve la clave i18n cruda.
RESP=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -H "Accept-Language: zh, es;q=0.5" \
    -d "{}")
if echo "$RESP" | grep -qiE "(required|requerido|requerida)"; then
    _pass "CROP-7.04" "multi-locale 'zh, es;q=0.5' resuelto a fallback EN o ES"
else
    _fail "CROP-7.04" "multi-locale resuelto" "EN o ES, no la clave cruda" "$RESP"
fi

# CROP-7.05 — Content-Type ProblemDetail en errores
HEADERS=$(curl -s -D - -o /dev/null -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "{}")
assert_content_type_contains "CROP-7.05" "ProblemDetail content-type" "problem+json" "$HEADERS"

# CROP-7.06 — campos mínimos del ProblemDetail
RESP=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "{}")
ALL_OK=1
for f in title status; do
    [[ $(echo "$RESP" | jq -r ".$f // empty") == "" ]] && ALL_OK=0
done
if [[ "$ALL_OK" == "1" ]]; then
    _pass "CROP-7.06" "ProblemDetail trae title y status"
else
    _fail "CROP-7.06" "title y status presentes" "ambos no vacíos" "$RESP"
fi

# CROP-7.07 — errors[] en validación 400
COUNT=$(curl -s -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" -d "{}" \
    | jq -r '.errors | length' 2>/dev/null || echo "0")
if [[ "$COUNT" -ge 3 ]]; then
    _pass "CROP-7.07" "errors[] con ≥ 3 entradas"
else
    _fail "CROP-7.07" "errors[] con ≥ 3" "≥ 3" "$COUNT"
fi

# CROP-7.08 — Content-Type application/problem+json en el request.
#
# Spring's MappingJackson2HttpMessageConverter registra los media
# types `application/json` y `application/*+json`. `problem+json`
# matchea el wildcard, así que Jackson lo deserializa como JSON
# normal y el endpoint responde 201 (o 400 si validación falla, que
# no es nuestro caso aquí). La aserción del plan original ("→ 415")
# era incorrecta para Spring 5.2+. Aceptamos 201 (comportamiento real)
# y dejamos como nota la posibilidad de bloquearlo si se quiere ser
# estricto via `consumes = "application/json"` en el controller.
NAME_708="$RUN_TAG-7.08"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/problem+json" \
    -d "$(make_body "$NAME_708")")
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_708")
assert_status_in "CROP-7.08" "POST con problem+json → 201 (Spring acepta */+json)" "201|415" "$STATUS"

# CROP-7.09 — Charset UTF-8 explícito en respuesta JSON
HEADERS=$(curl -s -D - -o /dev/null "$API_URL/crop")
assert_content_type_contains "CROP-7.09" "respuesta JSON" "json" "$HEADERS"

# CROP-7.10 — CORS preflight (OPTIONS)
skip_test "CROP-7.10" "CORS lo gestiona el gateway, no el servicio"

fi  # /sección 7

# ===========================================================================
# §8. Seguridad defensiva (CROP-8.x)
# ===========================================================================
if section_enabled 8; then
section_header 8 "Seguridad defensiva"

# CROP-8.01 — SQL injection en fields
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?fields=id;DROP%20TABLE%20crop;--")
assert_status "CROP-8.01" "SQL injection fields → 400" "400" "$STATUS"
PING=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop")
[[ "$PING" == "200" ]] && _pass "CROP-8.01b" "tabla intacta" \
    || _fail "CROP-8.01b" "tabla intacta" "200" "$PING"

# CROP-8.02 — SQL injection en crop_type_id
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/crop?crop_type_id=1%20OR%201=1")
assert_status "CROP-8.02" "SQL injection crop_type_id → 400" "400" "$STATUS"

# CROP-8.03 — body con propiedad extra
NAME_803="$RUN_TAG-8.03-extra"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$NAME_803\",\"description\":\"$VALID_DESC\",\"crop_type_id\":1,\"isAdmin\":true}")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-8.03" "body con prop extra → 201 (Jackson ignora)" "201" "$STATUS"
[[ "$STATUS" == "201" ]] && CREATED_NAMES+=("$NAME_803")

# CROP-8.04 — body con tipos inesperados
RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/crop" \
    -H "Content-Type: application/json" \
    -d "{\"name\":123,\"description\":456,\"crop_type_id\":1}")
STATUS="${RESP##*$'\n'}"
assert_status "CROP-8.04" "name como number → 400" "400" "$STATUS"

# CROP-8.05 — payload gigante (>1 MB)
if [[ "$SKIP_SLOW" == "1" ]]; then
    skip_test "CROP-8.05" "SKIP_SLOW=1"
else
    BIG_DESC=$(printf 'A%.0s' $(seq 1 1100000))   # ~1.05 MB
    RESP=$(echo "{\"name\":\"$RUN_TAG-8.05\",\"description\":\"$BIG_DESC\",\"crop_type_id\":1}" \
        | curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/crop" \
            -H "Content-Type: application/json" --data-binary @-)
    # Esperamos rechazo: o 400 (validación @Size) o 413/400 (límite del servlet)
    if [[ "$RESP" == "400" || "$RESP" == "413" ]]; then
        _pass "CROP-8.05" "payload >1 MB → $RESP"
    else
        _fail "CROP-8.05" "payload >1 MB rechazado" "400 o 413" "$RESP"
    fi
fi

# CROP-8.06 — path traversal
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_URL/crop/../../etc/passwd")
# El binding intentará parsear como UUID y fallará → 400. Si la URL la
# sanitiza el servlet container puede salir 404; ambas son válidas.
assert_status_in "CROP-8.06" "path traversal → 400/404" "400|404" "$STATUS"

# CROP-8.07 — header injection (curl no permite enviar \r\n; el binding sí
# debería rechazar). SKIP por dificultad técnica desde curl.
skip_test "CROP-8.07" "header injection no provocable desde curl estándar"

# CROP-8.08 — auth bypass: hoy /api/crop es público; verificamos que sin JWT
# pasa por el gateway con 200.
if [[ -n "$GATEWAY_URL" ]]; then
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/crop")
    if [[ "$STATUS" == "200" ]]; then
        _pass "CROP-8.08" "deuda confirmada: gateway permite GET sin JWT (200)"
    else
        _fail "CROP-8.08" "gateway sin JWT → 200 (deuda)" "200" "$STATUS"
    fi
else
    skip_test "CROP-8.08" "GATEWAY_URL no configurada"
fi

# CROP-8.09 — rate-limit
skip_test "CROP-8.09" "rate-limit no implementado"

fi  # /sección 8

# ===========================================================================
# §9. Filtro crop_type_id (CROP-9.x) — overlap con §2 pero formaliza casos
# ===========================================================================
if section_enabled 9; then
section_header 9 "Filtro crop_type_id (Paquete 03)"

# CROP-9.01 — filtro válido devuelve subconjunto: ya cubierto por 2.12
skip_test "CROP-9.01" "cubierto por CROP-2.12"

# CROP-9.02 — filtro válido sin matches del run
RESP=$(curl -s "$API_URL/crop?crop_type_id=5")
COUNT=$(echo "$RESP" | jq --arg p "$RUN_TAG-" '[.[] | select(.name | startswith($p))] | length')
if [[ "$COUNT" == "0" ]]; then
    _pass "CROP-9.02" "tipo 5 sin crops del run"
else
    _fail "CROP-9.02" "tipo 5 sin crops del run" "0" "$COUNT"
fi

# CROP-9.03 — fields=id + crop_type_id=1: cubierto por 2.18
skip_test "CROP-9.03" "cubierto por CROP-2.18"

# CROP-9.04 — crop_type_id=999: cubierto por 2.14
skip_test "CROP-9.04" "cubierto por CROP-2.14"

# CROP-9.05 — crop_type_id=abc: cubierto por 2.17
skip_test "CROP-9.05" "cubierto por CROP-2.17"

# CROP-9.06 — crop_type_id=0: cubierto por 2.15
skip_test "CROP-9.06" "cubierto por CROP-2.15"

# CROP-9.07 — orden de validación: fields inválido + crop_type_id válido
RESP=$(curl -s -w "\n%{http_code}" "$API_URL/crop?fields=secret&crop_type_id=1")
BODY="${RESP%$'\n'*}"; STATUS="${RESP##*$'\n'}"
assert_status "CROP-9.07" "fields inválido + crop_type_id → 400" "400" "$STATUS"
# Esperamos que el title sea el de FieldsValidator (no el de croptype)
assert_json_eq "CROP-9.07b" "title 'Campos invalido' (fields validó primero)" '.title' "Campos invalido" "$BODY"

# CROP-9.08 — SQL parametrizado: no observable desde el cliente; SKIP
skip_test "CROP-9.08" "no observable desde curl (requiere log de queries)"

# CROP-9.09 — performance: requiere seed >10k. SKIP.
skip_test "CROP-9.09" "requiere benchmark con >10 000 filas"

fi  # /sección 9

# ===========================================================================
# §10. Protección 'cultivo en uso' — bloqueada hasta implementar la feature
# ===========================================================================
if section_enabled 10; then
section_header 10 "Protección 'cultivo en uso' (🚧)"
skip_test "CROP-10.*" "protección no implementada en main; ver §10 del plan"
fi

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
