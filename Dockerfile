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

# Logs go to $SPOTIFY_MCP_HOME/logs (see McpHome). Credentials come from
# `docker run --env-file`, so there is no .env inside the image. appuser needs
# write access to /app to create the logs directory, or the file logback
# appender fails to start.
ENV SPOTIFY_MCP_HOME=/app
RUN useradd --create-home appuser && mkdir -p /app/logs && chown -R appuser:appuser /app
USER appuser

# STDIO transport: no port to expose, no HEALTHCHECK -- the process
# communicates over stdin/stdout with whatever launches it (docker run -i).
ENTRYPOINT ["java", "-jar", "app.jar"]
