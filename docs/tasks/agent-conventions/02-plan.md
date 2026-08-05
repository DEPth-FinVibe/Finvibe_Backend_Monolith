# AI 아키텍처·코드·오류 처리 컨벤션 도입 Plan

Overview 컨펌 완료. 이 문서는 컨벤션 문서 작성 단계와 사용자 결정 포인트 `D1`~`D9`를 정의한다.

## 목표 산출물

```text
AGENTS.md
docs/agent/
├── workflow.md
├── architecture-conventions.md
├── code-conventions.md
└── error-handling-conventions.md
```

`AGENTS.md`에는 규칙 본문을 넣지 않고 작업 유형별 문서 경로만 둔다. 상세 문서는 서로 내용을 중복하지 않는다.

## 완료 조건

- 현재 패키지 루트와 오류 응답 계약을 사실과 다르게 설명하지 않는다.
- 신규·변경 코드에 적용할 목표 규칙과 기존 legacy 예외가 구분되어 있다.
- 아키텍처 작업은 architecture 문서, Java·테스트 작업은 code 문서, 오류 계약 작업은 error 문서만 추가로 읽을 수 있다.
- 모듈 및 레이어 의존 방향, port/adapter 위치, DTO 노출 규칙이 명확하다.
- `DomainException -> DomainErrorHttpMapper -> GlobalExceptionHandler -> ErrorResponse` 흐름이 현재 코드와 일치한다.
- 기존 Java 코드, 설정, API 계약은 이 작업에서 변경하지 않는다.
- 문서 링크, 경로, 중복, Markdown 형식을 검증한다.

## 구현 단계

### S1. Architecture conventions

`docs/agent/architecture-conventions.md`를 작성한다.

- 실제 루트 패키지와 최상위 책임
- 모듈 내부 표준 레이어
- 레이어 간 의존 방향
- 모듈 간 공개 계약
- port/adapter 네이밍과 위치
- JPA 및 테이블 경계
- legacy 구조를 만났을 때의 처리 원칙

관련 결정: `D1`, `D2`, `D3`, `D4`, `D5`

검증:

- 문서의 예시 경로가 현재 `depth.finvibe` 기준인지 검색한다.
- `shared`, `depth.finvibe.investment`가 현재 경로인 것처럼 남아 있지 않은지 확인한다.
- 허용·금지 의존 방향이 서로 모순되지 않는지 검토한다.

### S2. Code conventions

`docs/agent/code-conventions.md`를 작성한다.

- 기본 작성 원칙과 네이밍
- domain/application/dto/infra 계층별 책임
- Spring·관측성 의존의 허용 범위
- Lombok 사용 범위
- 테스트 작성·검증 방식
- 포맷과 legacy 파일 수정 원칙

관련 결정: `D6`, `D7`, `D8`

검증:

- Wallet을 포함한 실제 클래스명과 디렉터리 예시가 존재하는지 확인한다.
- 현재 저장소에 없는 formatter 명령을 필수 검증으로 적지 않았는지 확인한다.
- 기존 파일 전체 재포맷을 유도하는 표현이 없는지 확인한다.

### S3. Error-handling conventions

`docs/agent/error-handling-conventions.md`를 작성한다.

- 계층별 오류 처리 책임
- 현재 공통 오류 계약
- 표준 예외 변환 흐름
- 모듈별 error code와 HTTP mapper 추가 절차
- HTTP status 기본 정책
- 테스트 포인트

관련 결정: `D9`

검증:

- `DomainErrorCode`, `DomainException`, `ErrorResponse`, `DomainErrorHttpMapper`, `GlobalExceptionHandler`의 현재 메서드·필드와 대조한다.
- `messageKey`를 현재 응답 계약인 것처럼 기술하지 않았는지 확인한다.

### S4. Progressive disclosure routing

`AGENTS.md`의 Context Routing 표에 세부 문서의 로딩 조건을 추가한다.

- 패키지·모듈 경계·port/adapter 변경 → architecture
- Java 코드·테스트 변경 → code
- 예외·error code·HTTP 오류 응답 변경 → error handling

여러 조건에 해당할 때만 복수 문서를 읽도록 명시한다.

검증:

- `AGENTS.md`에 상세 규칙이 중복되지 않았는지 확인한다.
- 모든 링크의 대상 파일이 존재하는지 확인한다.

### S5. Final review and result

- Markdown과 공백 오류를 검사한다.
- 변경 목록에 Java·설정 파일이 포함되지 않았는지 확인한다.
- 결정 `D1`~`D9`가 문서에 반영됐는지 대조한다.
- `04-result.md`에 실제 반영 내용과 검증 결과를 기록한다.

## 결정 포인트

### D1. 루트 패키지와 공통 영역

선택지:

- A. 제공 문서의 `depth.finvibe.investment`와 `shared`를 목표 구조로 채택하고 마이그레이션을 전제한다.
- B. 현재 구조인 `depth.finvibe`와 `common`을 공식 기준으로 사용한다.

AI 추천: **B**. 현재 모놀리스 전체가 사용하는 실제 경로이며, 이름 변경은 문서화와 분리된 대규모 마이그레이션이다.

### D2. 컨벤션의 적용 범위

선택지:

- A. 기존 코드 전체를 즉시 준수 대상으로 보고 위반 사항을 함께 수정한다.
- B. 신규 코드와 이번 요청으로 수정하는 코드에 적용하고, untouched legacy는 별도 작업 전까지 허용한다.

AI 추천: **B**. 규칙 도입과 구조 리팩터링을 분리해 요청 범위와 diff를 통제할 수 있다. 기존 코드를 수정할 때는 새 규칙을 적용하되 인접 영역까지 확장하지 않는다.

### D3. 표현 계층 패키지 이름

선택지:

- A. 신규 모듈의 표준을 `api`로 정하고 기존 `presentation`은 legacy로 유지한다.
- B. `api`와 `presentation`을 동등한 표준으로 허용한다.
- C. 기존 `presentation`도 이번 작업에서 `api`로 이동한다.

AI 추천: **A**. 다수 모듈과 사용자 제공 기준이 `api`를 사용한다. 기존 이동은 별도 리팩터링으로 남긴다.

### D4. `common`의 최소화 원칙

선택지:

- A. 현재 `common`의 비즈니스 패키지를 이번 작업에서 각 모듈로 이동한다.
- B. 기존 패키지는 legacy로 유지하고, 새 비즈니스 타입을 `common`에 추가하지 않는 규칙만 적용한다.

AI 추천: **B**. `common` 정리는 의존성 영향 분석이 필요한 별도 아키텍처 작업이다. 새 공통 타입은 여러 모듈에서 실제로 공유되고 도메인 중립적인 경우에만 허용한다.

### D5. 모듈 간 호출 계약 위치

선택지:

- A. 다른 모듈에 공개하는 내부 계약을 `modules/<owner>/api/internal`에 둔다.
- B. 호출 모듈이 대상 모듈의 `application/port/in`을 직접 사용한다.
- C. 현재처럼 필요한 domain 타입을 직접 참조하는 것을 허용한다.

AI 추천: **A**. 모듈 소유자가 내부 공개 DTO·인터페이스를 명시할 수 있고 domain 직접 노출을 막는다. 기존 직접 참조는 별도 작업에서 점진 제거한다.

### D6. Application에서 허용할 프레임워크 의존

선택지:

- A. Application에서 Spring·Micrometer를 포함한 모든 프레임워크 타입을 금지하고 전부 port로 감싼다.
- B. `@Service`, `@Transactional`, 로깅과 관측성은 허용하되 DB·Kafka·Redis·외부 API 기능 의존은 port로 역전한다.
- C. 기술 의존을 제한하지 않는다.

AI 추천: **B**. 현재 Spring 모놀리스의 실용성과 핵심 비즈니스 의존 역전을 함께 유지한다. 관측 코드가 비즈니스 흐름을 왜곡하거나 테스트를 어렵게 만들면 별도 adapter 추출을 검토한다.

### D7. 포맷과 들여쓰기

선택지:

- A. 탭을 공식 표준으로 정하고 수정 파일을 모두 탭으로 변환한다.
- B. 공백 4칸을 공식 표준으로 정하고 수정 파일을 모두 변환한다.
- C. formatter 도입 전까지 기존 파일의 주변 스타일을 유지하고, 요청과 무관한 전체 재포맷을 금지한다.

AI 추천: **C**. 현재 자동 formatter 설정이 없고 스타일이 혼재한다. 일관된 강제 포맷은 formatter 도입 작업에서 결정하는 편이 재현 가능하다.

### D8. 테스트 컨벤션 적용 범위

선택지:

- A. 기존 테스트 전체를 새 네이밍과 given/when/then 형식으로 변경한다.
- B. 신규·변경 테스트부터 JUnit 5 + AssertJ, 행위 중심 한국어 `@DisplayName`, given/when/then 흐름, `DomainException.errorCode` 검증을 적용한다.

AI 추천: **B**. 테스트 동작과 무관한 대규모 변경을 피하면서 점진적으로 통일할 수 있다.

### D9. 오류 메시지 계약

선택지:

- A. 현재 구현에 맞춰 `getMessage()`와 `ErrorResponse.status/code/message/fieldErrors`를 공식 기준으로 문서화한다.
- B. 제공 규칙의 `messageKey`와 `{code, messageKey}`를 목표 계약으로 적고 이번 작업에서 코드도 전환한다.
- C. 현재 계약을 설명하되 `messageKey` 전환을 권고사항으로 함께 적는다.

AI 추천: **A**. 오류 응답은 외부 API 계약이므로 문서 작업에서 변경하지 않는다. i18n이 필요해지면 호환성과 클라이언트 영향을 포함한 별도 작업으로 다룬다.

## 추천 결정 요약

| ID | 주제 | 추천 |
|---|---|---|
| D1 | 루트·공통 패키지 | 현재 `depth.finvibe` + `common` |
| D2 | 적용 범위 | 신규·변경 코드부터 |
| D3 | 표현 계층 | 신규는 `api`, 기존 `presentation` 유지 |
| D4 | common 최소화 | 신규 유입 제한, 기존은 별도 정리 |
| D5 | 모듈 간 계약 | 소유 모듈의 `api/internal` |
| D6 | Application 의존 | Spring·관측 허용, 기능 I/O는 port |
| D7 | 포맷 | 주변 스타일 유지, 전체 재포맷 금지 |
| D8 | 테스트 | 신규·변경 테스트부터 적용 |
| D9 | 오류 메시지 | 현재 `message` 계약 유지 |

## 커밋 계획

1. `docs: AI 컨벤션 도입 overview`
2. `docs: AI 컨벤션 구현 계획`
3. `docs: 아키텍처 컨벤션 추가`
4. `docs: 코드 및 테스트 컨벤션 추가`
5. `docs: 오류 처리 컨벤션 추가`
6. `docs: 에이전트 컨텍스트 라우팅 정리`
7. `docs: AI 컨벤션 result`

각 구현 커밋은 관련 결정이 `03-decisions.md`에 기록된 뒤 만든다.
