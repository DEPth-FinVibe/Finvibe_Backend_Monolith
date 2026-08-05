# AI 아키텍처·코드·오류 처리 컨벤션 결정 이력

각 항목은 검토한 대안, 선택, 선택 이유와 트레이드오프를 기록한다.

## D1. 루트 패키지와 공통 영역

### 대안

1. 현재 구조인 `depth.finvibe` + `common` 유지
2. `depth.finvibe.investment` + `shared`로 전체 변경
3. `depth.finvibe`는 유지하고 `common`만 `shared`로 변경

### 선택

**1번: `depth.finvibe` + `common` 유지**

### 이유

- `depth.finvibe`는 특정 도메인이나 현재 배포 구조를 루트 이름에 고정하지 않아, 모놀리스 확장과 향후 서비스 분리에 모두 사용할 수 있다.
- `depth.finvibe.investment`는 현재의 투자 도메인 묶음을 루트에 고정해 향후 wallet, user 등 독립 서비스 분리 시 부자연스러운 이름이 된다.
- `common`을 `shared`로 이름만 변경해도 모듈 결합도는 낮아지지 않는다. MSA 전환 가능성은 공통 영역의 이름보다 공유 범위와 모듈 간 계약에 좌우된다.
- 대규모 package/import 변경 없이 현재 코드와 일치하는 기준을 확립할 수 있다.

### 전제와 트레이드오프

- `common`에는 여러 모듈이 실제로 공유하는 도메인 중립적 기반 타입과 횡단 관심사만 새로 추가한다.
- 기존 `common`의 비즈니스 성격 패키지는 자동으로 정당화하지 않으며, 별도 작업에서 점진적으로 분리한다.
- 모듈 간 domain 객체와 JPA Entity 공유를 피하고, 호출 측 output port와 adapter를 통해 경계를 격리하는 방향을 후속 결정에서 구체화한다.
- MSA로 전환할 때 서비스별 최종 루트 패키지 변경은 발생할 수 있지만, 모듈 경계가 보존되면 이동 범위가 해당 모듈 내부로 제한된다.

## D2. 컨벤션 적용 범위

### 대안

1. 기존 코드 전체를 한 작업에서 즉시 준수하도록 변경
2. 신규·변경 코드에만 적용하고 기존 위반은 유지
3. 전체 준수를 목표로 하되 신규·변경 코드에는 즉시 적용하고 기존 위반은 독립 작업으로 점진 정리

### 선택

**3번: 전체 준수를 목표로 한 단계적 적용**

### 이유

- 기존 코드 전체를 조사한 결과 메인 Java 641개 중 최소 166개 기존 파일이 컨벤션 정리의 직접 영향 후보로 확인됐다.
- `common` 경계만 보더라도 비전역 파일 45개와 소비 파일 92개가 연결되어 있어, 컨벤션 문서 도입과 함께 변경하면 검증·리뷰 범위가 지나치게 커진다.
- 현재 테스트는 6개 파일, 18개 테스트 메서드로 대규모 일괄 리팩터링의 회귀를 보호하기에 부족하다.
- 목표를 낮추는 것이 아니라 구조 변경을 독립적인 gate와 검증 단위로 분리하는 편이 MSA 재분리 가능성을 더 안전하게 확보한다.

### 적용 방식

- 신규 코드는 확정된 컨벤션을 모두 준수한다.
- 기존 코드를 수정할 때는 변경 범위에서 새 위반을 만들지 않고, 가능한 범위에서 해당 규칙을 적용한다.
- 요청과 관계없는 legacy까지 같은 변경에 섞지 않는다.
- 기존 위반은 영향 범위와 검증 방법을 갖춘 별도 task와 브랜치에서 정리한다.
- `common` 부분 정리는 `docs/common-boundary-cleanup` 브랜치의 별도 작업으로 분리했고, `01-overview.md`까지만 확정했다.

### 트레이드오프

- 한동안 목표 컨벤션과 legacy 구조가 공존한다.
- 에이전트 문서에는 목표 규칙과 기존 예외를 구분해 적어야 한다.
- 단계별 작업이 누락되지 않도록 별도 task나 backlog로 추적해야 한다.

## D3. 표현 계층 패키지 이름

### 대안

1. 신규 표준은 `api`로 정하고 기존 `presentation`은 legacy로 유지
2. `api`와 `presentation`을 모두 공식 허용
3. 기존 `presentation` Controller를 현재 작업에서 `api`로 이동

### 선택

**3번: 기존 Controller까지 `api`로 이동해 즉시 통일**

### 이유

- 전체 표현 계층을 `api`로 통일하면 새 클래스를 배치할 때 예외 규칙이 필요 없다.
- 현재 `presentation` 아래에는 discussion과 news의 Controller 네 개만 있어 영향 범위가 작고 참조도 없다.
- HTTP endpoint와 클래스 내용은 유지하고 Java package 경로만 변경하므로 독립적인 대규모 히스토리가 필요한 작업이 아니다.

### 적용 범위

- `modules/discussion/presentation/external` -> `modules/discussion/api/external`
- `modules/news/presentation/external` -> `modules/news/api/external`
- Controller 네 개의 package 선언 변경
- 컴파일을 통한 component scan과 참조 검증

## D4. `common` 최소화 적용 방식

### 대안

1. 현재 `common` 구조를 공식 기준으로 그대로 인정
2. 기존 구조를 legacy 예외로 유지하고 신규 유입만 제한
3. 공통 계약·오류·기반 타입은 남기고 비즈니스 domain과 모듈 전용 infra를 별도 작업에서 부분 정리

### 선택

**3번: `common` 경계를 별도 작업에서 부분 정리**

### 이유

- 이벤트 계약과 전역 오류 타입처럼 여러 모듈이 실제로 공유할 수 있는 요소까지 해체할 필요는 없다.
- 특정 domain, JPA repository, Kafka producer와 같은 구현이 `common`에 남으면 향후 서비스 분리 시 소유권과 이동 범위가 불명확해진다.
- 잠재 영향 범위가 최대 128개 기존 파일이므로 AI 컨벤션 문서 도입과 같은 변경으로 묶지 않는다.

### 적용 방식

- 현재 컨벤션 문서에는 `common`의 목표 원칙과 기존 구조의 점진 정리 방침을 함께 적는다.
- 현재 브랜치에서는 `common` Java 코드를 이동하지 않는다.
- 별도 `docs/common-boundary-cleanup` 브랜치를 `main`에서 생성했다.
- 별도 작업의 `01-overview.md`를 커밋 `2f38443`으로 확정했다.
- 후속 plan과 구현은 해당 작업에서 사용자 gate를 거쳐 진행한다.

## D5. 모듈 간 호출 계약

### 대안

1. 호출 모듈이 대상 모듈의 application port를 직접 의존
2. 호출 모듈이 output port를 소유하고 infra adapter가 대상 모듈의 input port 또는 내부 transport 계약을 호출
3. 모든 모듈 간 통신을 비동기 이벤트로 전환

### 선택

**2번: caller-owned output port + infra adapter**

### 이유

- 현재 asset, trade, news 등 주요 교차 모듈 호출이 이미 이 구조를 사용한다.
- 호출 모듈의 application은 자신이 필요한 기능만 output port로 정의하므로 대상 모듈 구현을 모른다.
- 모놀리스에서는 infra adapter가 대상 모듈의 `application/port/in`을 직접 호출해 불필요한 HTTP 통신과 중복 interface를 피할 수 있다.
- MSA 전환 시 application 코드는 유지하고 infra adapter만 HTTP, gRPC 또는 messaging client로 교체할 수 있다.
- 즉시 결과가 필요한 조회·검증은 동기 port를 사용하고, 상태 전파·알림은 이벤트를 사용할 수 있다.

### 세부 규칙

- 호출 모듈의 `application/port/out`은 호출 모듈이 소유한 원시 타입 또는 contract DTO를 반환한다.
- 호출 모듈의 application port가 대상 모듈의 domain이나 DTO를 import하지 않는다.
- in-process adapter는 대상 모듈의 `application/port/in`을 호출할 수 있다.
- `api/external`은 클라이언트에 공개하는 HTTP API다.
- `api/internal`은 서비스 간 통신에 공개하는 내부 HTTP API다.
- `api/internal`과 `api/external`은 전송 계층의 공개 범위를 나타내며 application port와 다른 개념이다.
- `application/port/in`은 application이 제공하는 유스케이스 계약이고, `application/port/out`은 application이 외부에 요구하는 의존 계약이다.
- 모놀리스의 in-process 호출에 `api/internal` HTTP 경로를 강제하지 않는다. MSA 분리 후 infra adapter가 대상 서비스의 `api/internal` endpoint를 호출한다.
- 비동기 처리가 자연스러운 상태 변경은 event contract를 사용한다.

### 현재 확인된 legacy

- `asset.application.port.out.WalletClient`가 `wallet.dto.WalletDto`를 반환한다.
- `news.application.port.out.NewsDiscussionPort`가 discussion DTO를 노출한다.
- 일부 application과 Controller가 다른 모듈의 domain enum을 직접 import한다.

이 항목은 D2의 단계적 적용 원칙에 따라 별도 경계 정리 작업으로 다룬다. 현재 컨벤션 문서 작업에서 기능 코드를 함께 변경하지 않는다.

## D6. Application의 프레임워크 의존 범위

### 대안

1. Spring과 Micrometer를 포함한 모든 프레임워크 의존을 port로 추상화
2. Application 구성·트랜잭션·로깅·관측성은 허용하고 기능 I/O는 port로 역전
3. Application의 기술 의존을 제한하지 않음

### 선택

**2번: Spring·관측성 허용, 기능 I/O는 port로 분리**

### 허용

- `@Service`, `@Component` 등 application component 선언
- `@Transactional`을 통한 유스케이스 트랜잭션 경계
- SLF4J 로깅
- Micrometer metric 기록
- 입력 검증과 application orchestration에 필요한 Spring 기반 지원

### port로 분리

- DB와 Spring Data repository
- Redis
- Kafka producer와 외부 messaging
- 외부 HTTP API와 SDK
- 파일·object storage 등 외부 I/O

### 판단 기준

- 프레임워크 타입이 비즈니스 규칙과 유스케이스 계약을 오염시키지 않아야 한다.
- 관측 코드가 비즈니스 흐름을 크게 방해하거나 단위 테스트를 어렵게 만들면 별도 port/adapter 추출을 검토한다.
- application에서 특정 infra 구현 클래스를 직접 import하지 않는다.

### 현재 확인된 legacy

- `asset.application.RedisIndexSyncService`가 `asset.infra.redis` 구현 세 개를 직접 import한다.
- `news.application.port.in.NewsQueryUseCase`가 Spring Data의 `Page`, `Pageable`을 유스케이스 계약에 노출한다.

이 항목은 D2에 따라 별도 작업 또는 해당 코드의 다음 변경 시 정리한다.
