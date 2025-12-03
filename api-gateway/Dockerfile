# ---------- BUILD STAGE ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos el código fuente
COPY . .

# Construimos el jar
RUN ./mvnw -q -e -DskipTests clean package

# ---------- RUN STAGE ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos el jar generado desde el stage anterior
COPY --from=build /app/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
#
#
#FROM openjdk:21-slim
#
#ARG PORT=8080
#EXPOSE ${PORT}
#
#ENV MQTTBROKERIP=192.168.18.31
#ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom --add-opens java.base/java.net=ALL-UNNAMED"
#ADD target/API4EventProcessing-0.0.1-SNAPSHOT.jar app.jar
#
#ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]