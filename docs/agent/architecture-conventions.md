# 아키텍처·패키지 컨벤션

패키지 생성, 모듈 경계, port/adapter, 모듈 간 호출 또는 영속성 구조를 변경할 때만 이 문서를 읽는다.

## 적용 원칙

- 신규 코드는 이 문서를 모두 따른다.
- 기존 코드를 수정할 때 변경 범위에 새 위반을 만들지 않는다.
- 요청과 무관한 legacy 구조는 함께 정리하지 않고 별도 task로 분리한다.
- package 이름보다 의존 방향과 데이터 소유권을 우선한다.

## 최상위 구조

기본 루트는 `depth.finvibe`다.

```text
depth.finvibe/
├── boot/       애플리케이션 부트스트랩, 설정, 보안, DI 조립
├── common/     도메인 중립 기반 타입과 전역 횡단 관심사
└── modules/    Bounded Context 단위 도메인 모듈
```

`depth.finvibe.investment` 또는 `shared`를 새 루트로 만들지 않는다.

## `common` 경계

새 코드는 다음 조건을 모두 만족할 때만 `common`에 둔다.

- 둘 이상의 모듈이 실제로 사용한다.
- 특정 Bounded Context의 비즈니스 의미를 소유하지 않는다.
- 모듈의 domain 객체나 JPA Entity를 노출하지 않는다.
- 공통 오류, 시간·기반 타입, 횡단 관심사 또는 명시적인 통합 계약이다.

비즈니스 domain, 모듈 전용 repository, Kafka producer, 외부 API client를 `common`에 새로 추가하지 않는다.

기존 `common.<context>` 패키지는 legacy다. 명시적인 정리 작업 없이 이동하지 않는다. 부분 정리는 별도 `docs/common-boundary-cleanup` 작업에서 다룬다.

## 모듈 구조

필요한 레이어만 만든다. 빈 패키지와 사용자가 요청하지 않은 추상화는 만들지 않는다.

```text
modules/<module>/
├── domain/
├── application/
│   └── port/
│       ├── in/
│       └── out/
├── api/
│   ├── external/
│   └── internal/
├── dto/
└── infra/
    ├── persistence/
    ├── messaging/
    ├── client/
    └── error/
```

### `domain`

- Entity, Value Object, domain service, enum과 비즈니스 규칙을 둔다.
- 개발 편의를 위해 JPA Entity를 domain model로 함께 사용할 수 있다.
- application, api, infra를 의존하지 않는다.

### `application`

- 유스케이스 조립, 트랜잭션 경계와 orchestration을 담당한다.
- 입력 계약은 `application/port/in/*UseCase`에 둔다.
- DB, Redis, Kafka, 외부 API 등 기능 I/O 계약은 `application/port/out`에 둔다.
- 기능 I/O의 구체 구현을 직접 import하지 않는다.

### `api`

- `api/external`: 클라이언트에 공개하는 HTTP API
- `api/internal`: 서비스 간 통신에 공개하는 내부 HTTP API
- Controller, transport request/response와 protocol validation을 둔다.
- domain model과 JPA Entity를 외부 계약으로 직접 노출하지 않는다.

`api/internal`과 `api/external`은 전송 계층의 공개 범위다. application의 `port/in`, `port/out`과 같은 개념이 아니다.

### `dto`

- 모듈의 유스케이스·API가 반환하는 명시적인 계약 타입을 둔다.
- domain에서 DTO로 변환하는 로직은 DTO의 `from` 정적 팩토리를 기본으로 한다.
- 다른 모듈의 DTO를 자신의 application port 계약에 직접 노출하지 않는다.

### `infra`

- persistence, messaging, 외부 client, Redis, scheduler 등 기술 구현을 둔다.
- Spring Data interface는 `infra/persistence/*JpaRepository`에 둔다.
- output port 구현은 역할에 따라 `*RepositoryImpl`, `*ClientImpl`, `*Producer`로 명명한다.
- 모듈별 HTTP error mapping은 `infra/error/*ErrorHttpMapper`에 둔다.

## 의존 방향

허용하는 기본 방향은 다음과 같다.

```text
api ───────────────→ application/port/in
application ───────→ domain
application ───────→ application/port/out
infra ─────────────→ application/port/in 또는 port/out
domain/application → common의 도메인 중립 계약
```

다음을 금지한다.

- `domain -> application/api/infra`
- `application -> infra 구현`
- 한 모듈의 application/domain -> 다른 모듈의 domain
- 다른 모듈의 JPA Entity를 application/API 계약으로 노출
- 편의를 위한 비즈니스 타입의 `common` 이동

## 모듈 간 동기 호출

호출 측 모듈이 필요한 기능을 자신의 output port로 정의한다.

```text
trade/application/port/out/WalletClient
                    ↑ implements
trade/infra/client/WalletClientImpl
                    ↓ in-process
wallet/application/port/in/WalletQueryUseCase
```

규칙:

- 호출 측 application은 대상 모듈을 모른다.
- 호출 측 output port는 호출 측이 소유한 원시 타입 또는 contract DTO를 사용한다.
- in-process infra adapter는 대상 모듈의 input port를 호출할 수 있다.
- 다른 모듈의 domain enum, Entity, DTO를 호출 측 port에 노출하지 않는다.

MSA로 분리한 뒤에는 application을 유지하고 infra adapter만 변경한다.

```text
trade/application/port/out/WalletClient
                    ↑ implements
trade/infra/client/WalletHttpClient
                    ↓ HTTP
wallet-service/api/internal
```

## 모듈 간 비동기 통신

- 상태 변경 전파, 알림과 최종 일관성이 자연스러운 흐름은 event를 사용한다.
- topic은 `{domain}.{event}.v1` 형태처럼 의미와 버전을 드러낸다.
- payload는 JPA Entity나 domain 객체가 아닌 독립 contract DTO로 정의한다.
- 생산자와 소비자의 schema 호환성을 확인하지 않고 package나 필드를 변경하지 않는다.
- 즉시 응답이 필요한 조회·검증을 무조건 event로 바꾸지 않는다.

## 영속성과 DB 경계

- 모듈의 persistence는 해당 모듈의 `infra/persistence`에 캡슐화한다.
- 다른 모듈 테이블을 JPA 연관관계로 직접 연결하지 않는다.
- 다른 모듈의 식별자는 scalar ID로 보관한다.
- 다른 모듈 데이터가 필요하면 output port를 통한 조회 또는 별도 read model을 사용한다.
- 한 모듈의 repository가 다른 모듈 Entity를 반환하지 않는다.

## 새 클래스 배치 체크리스트

```text
[ ] 이 클래스의 비즈니스 소유 모듈이 명확한가?
[ ] domain이 application/api/infra를 import하지 않는가?
[ ] application이 기능 I/O 구현 대신 port/out을 의존하는가?
[ ] 외부 클라이언트 API는 api/external에 있는가?
[ ] 서비스 간 HTTP API는 api/internal에 있는가?
[ ] 다른 모듈의 domain·DTO를 내 모듈 계약에 노출하지 않는가?
[ ] common에 두어야 할 실제 근거가 있는가?
[ ] 모듈 간 JPA 연관관계를 만들지 않았는가?
```
