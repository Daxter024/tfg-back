FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy source
COPY . .

RUN chmod +x mvnw
# Build the jar (tests skipped — they run in CI)
RUN ./mvnw -q -e -DskipTests clean package

# ---------- RUN STAGE ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /app/target/task-service-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
