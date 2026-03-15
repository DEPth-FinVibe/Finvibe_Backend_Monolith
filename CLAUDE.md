# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile and run tests (same as CI)
./gradlew clean compileJava test

# Run all tests
./gradlew test

# Run targeted tests
./gradlew test --tests "depth.finvibe.*"

# Run locally (default profile)
./gradlew bootRun

# Run with local + OAuth profiles
SPRING_PROFILES_ACTIVE=local,oauth ./gradlew bootRun

# Full build with packaging
./gradlew clean build

# Local Docker smoke test
docker build -t finvibe-backend-monolith:local .
```

## Local Infrastructure

Start dependent services (MariaDB, Redis, Kafka) via Docker Compose:

```bash
docker compose -f infra/docker-compose.yml up -d
```

Local DB: `jdbc:mariadb://localhost:3306/finvibe` (user: `finvibe`, pass: `finvibe`)

## Architecture

Spring Boot 4.0.1 monolith on Java 21, organized into domain modules under `src/main/java/depth/finvibe/`:

- **`boot/`** — Global configuration, startup initialization
- **`common/`** — Shared utilities, error handling, cross-cutting concerns
- **`modules/`** — Feature domains: `asset`, `discussion`, `gamification`, `market`, `news`, `study`, `trade`, `user`, `wallet`

Each module follows **hexagonal architecture**:
```
modules/<domain>/
├── domain/          # Entities, enums, error codes
├── application/     # Services, use cases
│   ├── port/in/     # Input port interfaces
│   └── port/out/    # Output port interfaces
├── api/ or presentation/  # REST controllers
├── infra/           # Persistence adapters, Kafka, external APIs
└── dto/             # Request/response DTOs
```

Cross-module calls must go through `port/in` and `port/out` contracts — never depend directly on another module's `infra` or `domain`.

## Key Technologies

| Concern | Technology |
|---------|-----------|
| Database | MariaDB 11 + Spring Data JPA + QueryDSL |
| Cache | Redis via Redisson |
| Messaging | Apache Kafka 3.7.0 |
| Scheduling | ShedLock (Redis provider) |
| Circuit Breaker | Resilience4j (KIS API) |
| Auth | JWT + OAuth2 (Google, Naver) |
| LLM | LangChain4j + Google Gemini |
| API Docs | Springdoc OpenAPI (`/swagger-ui.html`) |
| Observability | Spring Actuator + Prometheus + Loki |

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local MariaDB/Redis/Kafka addresses |
| `oauth` | OAuth2 client credentials |
| `prod` | Env-var-based production config |
| `test` | H2 in-memory DB, used automatically for tests |
| `kafka` | Kafka serialization overrides |

## Coding Conventions

- **Indentation**: Tabs (not spaces) in Java files
- **Packages**: lowercase (`depth.finvibe...`)
- **Classes**: PascalCase with role suffix: `*Controller`, `*Service`, `*Repository`, `*UseCase`, `*ErrorCode`
- **Methods/fields**: camelCase
- No formatter is enforced — match style of neighboring files

## Commit Format

```
<type>: <short summary in Korean or English, imperative>
```

Common types: `feat:`, `fix:`, `refactor:`. Recent history uses Korean summaries.

For PRs: list any config/env var changes explicitly and confirm CI passes before review.
