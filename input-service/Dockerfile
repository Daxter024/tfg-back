FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw -q -e -DskipTests clean package

# ---------- RUN STAGE ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/input-service-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
