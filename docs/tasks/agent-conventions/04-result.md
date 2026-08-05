# AI 아키텍처·코드·오류 처리 컨벤션 도입 Result

## 결과 요약

Finvibe Backend Monolith의 AI 문서를 progressive disclosure 구조로 정리했다. `AGENTS.md`는 21줄의 진입점으로 유지하고, 작업 종류에 따라 workflow와 세부 컨벤션만 선택해 읽도록 구성했다.

컨벤션과 현재 코드의 차이는 무조건적인 일괄 리팩터링으로 처리하지 않았다. 작은 범위인 표현 계층과 테스트 형식은 현재 작업에서 통일했고, 영향 범위가 큰 `common` 경계 정리는 별도 브랜치와 task로 분리했다.

## 문서 구조

```text
AGENTS.md
docs/agent/
├── workflow.md
├── architecture-conventions.md
├── code-conventions.md
└── error-handling-conventions.md
docs/tasks/agent-conventions/
├── 01-overview.md
├── 02-plan.md
├── 03-decisions.md
└── 04-result.md
```

### Progressive disclosure routing

| 작업 | 읽는 문서 |
|---|---|
| 저장소 변경 | `workflow.md` |
| 패키지·모듈 경계·port/adapter·영속성 변경 | `architecture-conventions.md` |
| Java·테스트 변경 | `code-conventions.md` |
| 예외·error code·HTTP 오류 응답 변경 | `error-handling-conventions.md` |

현재 작업에 해당하는 문서만 추가로 읽고 나머지는 미리 불러오지 않는다.

## 적용한 결정

### D1. 루트 패키지

- `depth.finvibe`와 `common`을 유지했다.
- 특정 도메인 이름을 루트에 고정하지 않아 모듈 확장과 MSA 재분리에 모두 대응한다.

### D2. 적용 범위

- 전체 준수를 목표로 하되 단계적으로 적용한다.
- 신규·변경 코드에는 즉시 적용하고 기존 위반은 독립 task로 정리한다.

### D3. 표현 계층

- discussion과 news의 Controller 네 개를 `presentation/external`에서 `api/external`로 이동했다.
- HTTP endpoint와 Controller 구현은 변경하지 않았다.
- 현재 `modules` 아래에 Java 파일을 포함한 `presentation` 패키지는 남아 있지 않다.

### D4. `common` 최소화

- 전역 계약은 유지하고 비즈니스 domain과 모듈 전용 infra만 부분 정리하는 방향을 선택했다.
- 별도 `docs/common-boundary-cleanup` 브랜치를 만들었다.
- 해당 브랜치에서 `01-overview.md`만 커밋 `2f38443`으로 확정했으며 plan과 구현은 시작하지 않았다.

### D5. 모듈 간 호출

- caller-owned output port와 infra adapter를 공식 기준으로 정했다.
- 모놀리스의 in-process adapter는 대상 모듈 input port를 호출할 수 있다.
- MSA 전환 후에는 caller application을 유지하고 adapter가 대상 서비스의 `api/internal` HTTP API를 호출한다.
- `api/internal`·`api/external`은 전송 계층이고 application port와 다른 개념임을 명시했다.

### D6. Application 의존

- Spring component, transaction, logging과 Micrometer는 허용했다.
- DB, Redis, Kafka, 외부 API 등 기능 I/O는 output port로 분리한다.

### D7. 포맷

- Java 들여쓰기는 공백 4칸으로 정했다.
- IntelliJ Reformat을 기본으로 하되 무관한 기존 코드 전체 재포맷은 금지했다.

### D8. 테스트

- 기존 테스트 6개 파일과 18개 테스트 메서드를 새 형식으로 통일했다.
- 한국어 `@DisplayName` 18개를 추가했다.
- 메서드 이름을 `대상_조건_결과` 형태로 바꾸고 given/when/then 흐름을 표시했다.
- 테스트 소스의 탭을 제거하고 공백 4칸으로 통일했다.
- 테스트 로직, 검증값과 커버리지는 변경하지 않았다.

### D9. 오류 메시지

- 현재 `DomainErrorCode.getMessage()` 계약을 유지했다.
- 외부 `ErrorResponse`의 `status`, `code`, `message`, 선택적 `fieldErrors`를 현재 기준으로 문서화했다.
- `messageKey`는 현재 계약에 포함하지 않았다.

## `CLAUDE.md` 정리

- 추적 중이던 `CLAUDE.md`를 삭제했다.
- `.gitignore`에 `CLAUDE.md`를 추가해 로컬 도구 파일이 다시 추적되지 않도록 했다.
- 모든 공용 AI 지침의 진입점은 `AGENTS.md`다.

## 검증

### Build

```text
./gradlew compileJava
BUILD SUCCESSFUL
```

Controller package 이동 후 컴파일 성공을 확인했다.

### Test

```text
./gradlew test
BUILD SUCCESSFUL
```

테스트 형식 변경 후 전체 테스트 성공을 확인했다.

Gradle은 `MarketKafkaProducerTest`의 unchecked operation 경고를 출력했지만 테스트 실패는 없었다. 이 경고는 기존 raw `KafkaTemplate` mock 사용에서 발생하며 이번 형식 변경 범위에는 포함하지 않았다.

### Static checks

- `git diff --check`: 통과
- D1~D9 결정 기록: 9개 확인
- `presentation` 아래 Java 파일: 0개
- 이동된 discussion/news Controller: 4개 확인
- `@Test`: 18개
- `@DisplayName`: 18개
- 테스트 소스 tab: 0개
- `CLAUDE.md`: ignore 규칙 적용 확인
- `AGENTS.md`의 모든 routing 대상 파일 존재 확인

## 구현 중 명확해진 사실

1. 기존 모듈 간 호출은 이미 caller output port + infra client 구조를 상당 부분 사용하고 있다.
2. 일부 caller port가 대상 모듈 DTO를 노출하고 일부 application/Controller가 다른 모듈 domain enum을 직접 import하는 legacy가 남아 있다.
3. `api/internal`은 서비스 간 HTTP API이고, `application/port/in`은 유스케이스 계약이므로 서로 대체 관계가 아니다.
4. `common` 전체를 즉시 정리하면 최대 128개 기존 파일이 영향받을 수 있어 별도 작업 분리가 필요했다.
5. 현재 formatter 설정이 없으므로 공백 4칸은 문서 규칙으로 적용하며 자동 강제는 후속 과제다.

## 남은 과제

- `docs/common-boundary-cleanup`의 plan과 결정 진행
- caller output port에서 다른 모듈 DTO 노출 제거
- application/Controller의 다른 모듈 domain enum 직접 참조 제거
- `asset.application.RedisIndexSyncService`의 infra repository 직접 의존 분리
- `NewsQueryUseCase`의 Spring Data `Page`, `Pageable` 계약 분리 검토
- 전용 `*ErrorHttpMapper`가 없는 모듈 오류 점검
- application의 일반 `IllegalArgumentException`, `IllegalStateException` 중 비즈니스 오류 분류
- `.editorconfig` 또는 formatter 도입 검토
- 부족한 domain·application·API 테스트 보강

이 항목들은 현재 컨벤션 작업에 섞지 않고 각각 영향 범위와 검증 기준을 가진 별도 task로 진행한다.

## 기존 작업 트리 보존

작업 시작 전에 존재하던 미추적 분석·Kafka·migration 문서와 스크립트는 수정하거나 커밋하지 않았다.
