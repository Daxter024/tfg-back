# ---------- BUILD STAGE ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -e -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/auth-service-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]