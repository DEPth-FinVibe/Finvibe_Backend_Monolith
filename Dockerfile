FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/logs

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail http://localhost:8080/actuator/health || exit 1

ENTRYPOINT [
  "java",
  "-XX:+ExitOnOutOfMemoryError",
  "-XX:+HeapDumpOnOutOfMemoryError",
  "-XX:HeapDumpPath=/app/logs",
  "-jar",
  "/app/app.jar"
]