#!/bin/bash

# Configuración
API_URL="http://localhost:8083"
USER_EMAIL="user_$(date +%s)@example.com"
USER_PASS="Password123A"
WRONG_PASS="WrongPass123"

# Colores para la salida
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

check_status() {
    if [ "$1" == "$2" ]; then
        echo -e "${GREEN}[OK] Recibido status $1 como se esperaba.${NC}"
    else
        echo -e "${RED}[ERROR] Se esperaba $2 pero se recibió $1.${NC}"
    fi
}

echo -e "\n=== PRUEBAS DE REGISTRO (HU-USR-01) ==="

echo "1. Registro válido..."
REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/register" \
     -H "Content-Type: application/json" \
     -d "{
       \"full_name\": \"Usuario Test\",
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$USER_PASS\",
       \"password_confirmation\": \"$USER_PASS\"
     }")
check_status "$REG_STATUS" "201"

echo "2. Registro con email duplicado (debe fallar)..."
DUP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/register" \
     -H "Content-Type: application/json" \
     -d "{
       \"full_name\": \"Otro Nombre\",
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$USER_PASS\",
       \"password_confirmation\": \"$USER_PASS\"
     }")
check_status "$DUP_STATUS" "409"

echo "3. Registro con contraseñas que no coinciden..."
MIS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/register" \
     -H "Content-Type: application/json" \
     -d "{
       \"full_name\": \"Mismatch\",
       \"email\": \"mismatch@example.com\",
       \"password\": \"$USER_PASS\",
       \"password_confirmation\": \"DiffPass123\"
     }")
check_status "$MIS_STATUS" "400"


echo -e "\n=== PRUEBAS DE LOGIN (HU-USR-02) ==="

echo "4. Login con usuario inexistente (debe fallar)..."
NON_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/login" \
     -H "Content-Type: application/json" \
     -d "{
       \"email\": \"notfound@example.com\",
       \"password\": \"$USER_PASS\"
     }")
check_status "$NON_STATUS" "404"

echo "5. Login con contraseña incorrecta (debe fallar)..."
PASS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/login" \
     -H "Content-Type: application/json" \
     -d "{
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$WRONG_PASS\"
     }")
check_status "$PASS_STATUS" "401"

echo "6. Login exitoso (obtener tokens)..."
LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/login" \
     -H "Content-Type: application/json" \
     -d "{
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$USER_PASS\"
     }")
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refresh_token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
    echo -e "${GREEN}[OK] Token obtenido correctamente.${NC}"
else
    echo -e "${RED}[ERROR] No se pudo obtener el token.${NC}"
fi


echo -e "\n=== PRUEBAS DE TOKEN Y REFRESH ==="

echo "7. Validar token correcto..."
VAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$API_URL/validate" \
     -H "Authorization: Bearer $TOKEN")
check_status "$VAL_STATUS" "200"

echo "8. Validar con token malformado..."
MAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$API_URL/validate" \
     -H "Authorization: Bearer un.token.falso")
check_status "$MAL_STATUS" "401"

echo "9. Rotación de refresh token..."
REF_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/refresh" \
     -H "Content-Type: application/json" \
     -d "{\"refresh_token\": \"$REFRESH_TOKEN\"}")
check_status "$REF_STATUS" "200"


echo -e "\n=== PRUEBAS DE PASSWORD (HU-USR-04) ==="

echo "10. Cambio de password con clave actual errónea..."
CHG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/password/change" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
       \"current_password\": \"$WRONG_PASS\",
       \"new_password\": \"NewPass123A\",
       \"new_password_confirmation\": \"NewPass123A\"
     }")
check_status "$CHG_STATUS" "401"

echo -e "\n=== PRUEBA DE LOGOUT ==="

echo "11. Logout..."
OUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/logout" \
     -H "Authorization: Bearer $TOKEN")
check_status "$OUT_STATUS" "204"

echo "12. Validar token después de logout (debe ser inválido)..."
VAL_OUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$API_URL/validate" \
     -H "Authorization: Bearer $TOKEN")
check_status "$VAL_OUT_STATUS" "401"

echo -e "\n--- Todas las pruebas finalizadas ---"
