# ---------- BUILD STAGE ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos el código fuente
COPY . .

RUN chmod +x mvnw
# Construimos el jar
RUN ./mvnw -q -e -DskipTests clean package

# ---------- RUN STAGE ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos el jar generado desde el stage anterior
COPY --from=build /app/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]