# AI 아키텍처·코드·오류 처리 컨벤션 도입 Overview

## 작업 목적

사용자가 제공한 아키텍처/패키지, 코드 작성, 오류 처리 규칙을 Finvibe Backend Monolith의 AI 작업 기준으로 정리한다.

`AGENTS.md`는 짧은 진입점으로 유지하고, 에이전트가 관련 코드를 변경할 때만 상세 컨벤션 문서를 읽는 progressive disclosure 구조를 사용한다.

## 사용자 제공 기준

제공된 규칙의 중심은 다음과 같다.

- `boot` / 최소 공통 영역 / `modules`로 최상위 책임 분리
- 모듈 내부를 `domain` / `application` / `api` / `infra` / `dto`로 분리
- `api -> application -> domain`, `infra -> application port` 의존 방향 유지
- 모듈 간 domain 직접 참조 금지, 공개 계약을 통한 호출
- JPA와 외부 기술 의존성을 모듈의 `infra`에 캡슐화
- 도메인 규칙과 불변식은 domain에서 `DomainException`으로 표현
- HTTP 상태 매핑은 공통 예외 처리기와 모듈별 `*ErrorHttpMapper`가 담당
- UseCase/Repository port와 adapter의 위치 및 접미사 통일
- DTO 변환, Lombok, 테스트 네이밍과 예외 검증 방식 통일

## 현재 저장소에서 확인한 사실

### 1. 패키지 루트와 공통 영역 이름이 제공 기준과 다르다

현재 모놀리스의 실제 루트는 `depth.finvibe`다.

```text
depth.finvibe/
├── boot/
├── common/
└── modules/
```

제공 문서의 `depth.finvibe.investment`와 `shared`를 그대로 사용하면 현재 코드와 맞지 않는다. Finvibe 문서에서는 실제 이름인 `depth.finvibe`와 `common`을 기준으로 적거나, 별도 마이그레이션 결정을 내려야 한다.

### 2. 기본 모듈 레이어는 이미 대부분 존재한다

`wallet`을 포함한 주요 모듈은 다음 구조를 사용한다.

```text
modules/<module>/
├── domain/
├── application/
│   └── port/
│       ├── in/
│       └── out/
├── api/ 또는 presentation/
├── dto/
└── infra/
```

입력 포트와 출력 포트는 주요 모듈 모두 `application/port/in`, `application/port/out`에 분리되어 있다. 새 문서에서는 출력 포트를 `application/*Repository`와 혼용하기보다 현재 구조인 `application/port/out`을 기준으로 삼는 편이 일관적이다.

### 3. 표현 계층 이름이 통일되어 있지 않다

- `asset`, `gamification`, `market`, `study`, `trade`, `user`, `wallet`: `api`
- `discussion`, `news`: `presentation`

신규 코드의 표준을 `api`로 정할지, 두 이름을 모두 허용할지 결정이 필요하다. 기존 패키지의 일괄 이동은 API 동작과 무관하게 큰 diff를 만들 수 있다.

### 4. `common`이 이미 최소 공통보다 넓다

`common` 아래에는 전역 오류 처리뿐 아니라 `gamification`, `insight`, `investment`, `user` 관련 패키지가 존재한다. 따라서 “공통 영역은 최소 계약만 둔다”는 목표와 현재 상태 사이에 차이가 있다.

이번 문서화 작업에서 이를 즉시 이동할지, 신규·수정 코드부터 적용하는 목표 규칙으로 둘지 구분해야 한다.

### 5. 일부 모듈 간 직접 domain 참조가 존재한다

예를 들어 `gamification.application.SquadService`가 `user.domain.enums.UserRole`을 직접 참조하고, 개발용 API도 user domain enum을 참조한다.

모듈 간 domain 직접 import 금지를 새 코드부터 적용할 수는 있지만, 기존 참조까지 이 작업에서 제거하면 계약 설계와 코드 변경이 함께 필요한 별도 리팩터링이 된다.

### 6. Wallet은 방향의 예시이지만 완성된 형식 기준은 아니다

Wallet에는 다음 패턴이 구현되어 있다.

- `WalletCommandUseCase`, `WalletQueryUseCase`
- `WalletRepository` 출력 포트와 `WalletRepositoryImpl` persistence adapter
- 불변 `Money` 연산과 `DomainException`
- `WalletDto.WalletResponse.from(wallet)` 변환
- `WalletErrorHttpMapper`를 통한 HTTP 상태 매핑

동시에 다음과 같은 현재 코드 특성도 확인됐다.

- 파일별 들여쓰기가 2칸과 4칸으로 섞여 있고 formatter/linter가 없다.
- 사용하지 않는 `UUID` import가 일부 남아 있다.
- `WalletService`가 `MeterRegistry`에 직접 의존한다.
- wallet 테스트가 아직 없다.

따라서 Wallet의 설계 방향은 참고하되 현재 구현의 모든 세부를 컨벤션으로 고정하지 않아야 한다.

### 7. 오류 응답 계약이 제공 문서와 다르다

현재 구현은 다음 계약을 사용한다.

- `DomainErrorCode`: `getCode()`, `getMessage()`
- `ErrorResponse`: `status`, `code`, `message`, 선택적 `fieldErrors`
- 모듈별 `DomainErrorHttpMapper`: error code를 HTTP status로 변환
- 매퍼가 없으면 `500 Internal Server Error`

제공 규칙의 `messageKey`와 `{ code, messageKey }` 응답은 현재 코드에 존재하지 않는다. 이를 문서에 사실처럼 적으면 AI가 기존 API 계약을 임의로 바꿀 위험이 있다. 현재 `message` 계약을 문서화할지, i18n `messageKey` 전환을 별도 마이그레이션으로 다룰지 결정해야 한다.

### 8. 테스트 기반이 제공 규칙보다 제한적이다

JUnit 5와 AssertJ는 사용 중이지만 wallet 테스트는 없고, 전체 테스트가 `given/when/then`, 한국어 `@DisplayName`, `행위_success/fail` 네이밍을 일관되게 따르지는 않는다.

이 규칙을 신규·변경 테스트에 적용할지, 기존 테스트 전체를 함께 정리할지 범위를 구분해야 한다.

## 해결할 문제

1. 제공된 규칙을 현재 모놀리스 패키지와 공개 계약에 맞게 보정한다.
2. 아키텍처, 코드, 오류 처리 규칙을 필요할 때만 읽을 수 있도록 상세 문서로 분리한다.
3. `AGENTS.md`에는 작업 유형별 문서 경로만 추가한다.
4. 현재 상태와 목표 규칙이 다른 항목은 “기존 코드의 사실”과 “새 코드의 원칙”을 구분한다.
5. 대규모 구조 변경이 필요한 위반 사항을 문서 도입 작업에 섞지 않는다.

## 제안 범위

### 포함

- `docs/agent/architecture-conventions.md` 작성
- `docs/agent/code-conventions.md` 작성
- `docs/agent/error-handling-conventions.md` 작성
- `AGENTS.md`의 context routing 표에 작업별 로딩 조건 추가
- 현재 코드와 맞지 않는 패키지명·오류 응답 설명 보정
- 신규 코드와 변경 코드에 적용할 규칙, 예외, 금지 방향 명시

### 제외

- 기존 패키지의 일괄 이동
- `common`을 `shared`로 이름 변경
- `presentation`을 `api`로 즉시 이동
- 기존 모듈 간 직접 domain 참조의 일괄 제거
- `message` 응답을 `messageKey` 기반 API로 변경
- Wallet 및 기존 테스트의 일괄 리팩터링
- formatter/linter 신규 도입

제외 항목은 필요하면 각각 독립 작업으로 계획한다.

## 예상 산출물 구조

```text
AGENTS.md
docs/agent/
├── workflow.md
├── architecture-conventions.md
├── code-conventions.md
└── error-handling-conventions.md
```

`AGENTS.md`는 다음과 같이 작업별로 필요한 문서만 연결한다.

| 작업 | 추가로 읽을 문서 |
|---|---|
| 패키지 생성, 모듈 경계·port·adapter 변경 | `architecture-conventions.md` |
| Java 코드 또는 테스트 작성·수정 | `code-conventions.md` |
| 예외, error code, HTTP 오류 응답 변경 | `error-handling-conventions.md` |

여러 조건에 해당하는 작업만 해당 문서를 함께 읽는다.

## 위험과 제약

- 목표 규칙을 현재 구현 사실처럼 적으면 AI가 요청 범위를 넘어선 정리를 시도할 수 있다.
- 반대로 현재 코드만 그대로 문서화하면 개선하려는 아키텍처 원칙이 약해질 수 있다.
- API 응답의 `message`와 `messageKey` 차이는 하위 호환성 문제이므로 문서 편집으로 결정할 수 없다.
- formatter가 없으므로 “IDE 자동 포맷”만으로는 에이전트와 개발 환경 사이의 결과가 완전히 같지 않을 수 있다.
- 현재 작업 트리에 이 작업 이전의 미커밋 변경과 미추적 파일이 있으므로 커밋 시 대상 파일을 명시적으로 제한해야 한다.

## 이번 단계의 완료 기준

- 제공된 컨벤션과 현재 저장소의 차이가 확인 가능한 코드 구조를 근거로 정리되어 있다.
- 문서화 작업과 대규모 코드 리팩터링의 범위가 분리되어 있다.
- 사용자가 overview의 문제 정의, 포함 범위, 제외 범위를 확인한 뒤에만 plan으로 진행한다.
