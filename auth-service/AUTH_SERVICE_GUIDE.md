# Guía de Ejecución y Pruebas: Auth Service

Este documento detalla los pasos para compilar, ejecutar y verificar el funcionamiento del microservicio de autenticación.

## 1. Requisitos Previos

*   Java 21 instalado.
*   Docker instalado (para la base de datos).
*   `jq` instalado (opcional, para formatear las respuestas JSON en la terminal).

## 2. Preparación e Inicio

Ejecuta los siguientes comandos desde la carpeta raíz del proyecto:

### Paso A: Levantar la Base de Datos
```bash
docker compose -f auth-service/docker-compose.yml up auth-db -d
```

### Paso B: Compilar el Microservicio
```bash
cd auth-service
./mvnw clean package -DskipTests
```

### Paso C: Arrancar la Aplicación (Perfil de Desarrollo)
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
*La aplicación estará disponible en: **http://localhost:8083***

---

## 3. Script de Pruebas Automatizado

Crea un archivo llamado `test-auth.sh` en la carpeta `auth-service/` y pega el siguiente contenido:

```bash
#!/bin/bash

# Configuración (Puerto 8083 para Docker/Prod)
API_URL="http://localhost:8083"
USER_EMAIL="test@example.com"
USER_PASS="Password123A"

echo "--- 1. REGISTRO ---"
curl -s -X POST "$API_URL/register" \
     -H "Content-Type: application/json" \
     -d "{
       \"full_name\": \"Juan Perez\",
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$USER_PASS\",
       \"password_confirmation\": \"$USER_PASS\"
     }" | jq .

echo -e "\n--- 2. LOGIN ---"
LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/login" \
     -H "Content-Type: application/json" \
     -d "{
       \"email\": \"$USER_EMAIL\",
       \"password\": \"$USER_PASS\"
     }")
echo $LOGIN_RESPONSE | jq .

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refresh_token')

echo -e "\n--- 3. VALIDAR TOKEN ---"
curl -s -o /dev/null -w "Status Code: %{http_code}\n" -X GET "$API_URL/validate" \
     -H "Authorization: Bearer $TOKEN"

echo -e "\n--- 4. REFRESH TOKEN ---"
curl -s -X POST "$API_URL/refresh" \
     -H "Content-Type: application/json" \
     -d "{ \"refresh_token\": \"$REFRESH_TOKEN\" }" | jq .

echo -e "\n--- 5. LOGOUT ---"
curl -s -o /dev/null -w "Status Code: %{http_code}\n" -X POST "$API_URL/logout" \
     -H "Authorization: Bearer $TOKEN"
```

Para ejecutarlo:
```bash
chmod +x test-auth.sh
./test-auth.sh
```

---

## 4. Catálogo de Endpoints

| Método | Endpoint | Descripción | Acceso |
| :--- | :--- | :--- | :--- |
| `POST` | `/register` | Crea un usuario con rol 'agricultor'. | Público |
| `POST` | `/login` | Valida credenciales y devuelve tokens. | Público |
| `POST` | `/refresh` | Genera un nuevo Access Token. | Público |
| `GET` | `/validate` | Verifica si el Access Token es válido. | Bearer Token |
| `POST` | `/logout` | Invalida el Access Token y Refresh Tokens. | Bearer Token |
| `POST` | `/password/forgot` | Inicia flujo de recuperación (ver logs). | Público |
| `POST` | `/password/reset` | Cambia clave usando token de reset. | Público |
| `POST` | `/password/change` | Cambia clave del usuario actual. | Bearer Token |
| `GET` | `/users` | Listado paginado de usuarios. | Admin |
| `GET` | `/audit` | Historial de acciones del sistema. | Admin |

## 5. Notas Importantes

1.  **Auditoría:** Todas las acciones de login (éxito/fallo) y cambios de datos se guardan en la tabla `audit_log`.
2.  **Mails:** El servicio no envía correos reales. El contenido de los emails (como el código de bienvenida o el link de reset) se imprime en los **logs de la consola** de Spring Boot.
3.  **Seguridad:** El sistema bloquea cuentas tras 5 intentos fallidos durante 15 minutos.
