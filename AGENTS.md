# Repository Guidelines

## Project Structure & Module Organization
This is a Gradle-based Spring Boot monolith targeting Java 21. Main code lives in `src/main/java/depth/finvibe`, organized by domain modules such as `modules/asset`, `modules/market`, `modules/discussion`, `modules/news`, and `modules/user`.

Each module generally follows a layered/hexagonal style:
- `domain`: entities, enums, error codes
- `application`: services and use cases
- `application/port/in` and `application/port/out`: input/output ports
- `infra` or `presentation/api`: adapters (persistence, controllers, messaging)

Configuration and runtime assets are in `src/main/resources` (`application-*.yml`, `prompts/`, `seed/`). Tests are in `src/test/java` and test config in `src/test/resources`.

## Build, Test, and Development Commands
- `./gradlew clean compileJava test`: compile and run tests (same core command as CI).
- `./gradlew bootRun`: run locally with default profile.
- `SPRING_PROFILES_ACTIVE=local,oauth ./gradlew bootRun`: run with local profile overrides.
- `./gradlew clean build`: full build including packaging.
- `docker build -t finvibe-backend-monolith:local .`: local image build smoke check.

## Coding Style & Naming Conventions
Use Java 21 and standard Spring idioms. Follow existing style in this repo:
- Tabs are used for indentation in Java files.
- Packages are lowercase (`depth.finvibe...`), classes are PascalCase, methods/fields are camelCase.
- Prefer suffixes that match role: `*Controller`, `*Service`, `*Repository`, `*UseCase`, `*ErrorCode`.
- Keep module boundaries explicit by depending on `port/in` and `port/out` contracts.

No formatter/linter is enforced in Gradle currently, so keep changes consistent with neighboring files.

## Testing Guidelines
Testing uses JUnit 5 via `spring-boot-starter-test` (`useJUnitPlatform()` enabled). Name tests `*Test` or `*Tests` and mirror package structure from `src/main/java` when adding coverage. Run:
- `./gradlew test` for all tests
- `./gradlew test --tests "depth.finvibe.*"` for targeted runs

Add integration tests for controller/repository changes and include `src/test/resources/application-test.yml` settings when needed.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit-style prefixes, commonly `feat:` and `refactor:` (occasionally `fix:`). Keep format:
- `<type>: <short summary>` (imperative, specific)

For PRs:
- include a concise description of behavior changes
- link related issues/tasks
- list config/env var changes explicitly
- confirm CI passes (`clean compileJava test`) before review
- 모든 커밋은 한국어로 작성되어야 합니다.