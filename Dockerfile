# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B -DskipTests package


FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/target/spotify-mcp-server.jar app.jar

RUN useradd --create-home appuser
USER appuser

# STDIO transport: no port to expose, no HEALTHCHECK -- the process
# communicates over stdin/stdout with whatever launches it (docker run -i).
ENTRYPOINT ["java", "-jar", "app.jar"]
