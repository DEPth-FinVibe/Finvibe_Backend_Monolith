# 코드·테스트 컨벤션

Java 코드 또는 테스트를 작성·수정할 때만 이 문서를 읽는다. 패키지와 모듈 경계는 `architecture-conventions.md`, 오류 계약은 `error-handling-conventions.md`를 따른다.

## 적용 원칙

- 가독성과 명확한 책임을 우선한다.
- 요청을 해결하는 최소 범위만 변경한다.
- 신규 코드는 이 문서를 모두 따른다.
- 기존 코드는 직접 변경하는 범위에 적용하며 무관한 정리를 섞지 않는다.
- 입력과 상태가 유효하지 않으면 빠르게 실패한다.

## 포맷

- Java 들여쓰기는 공백 4칸을 사용한다.
- IntelliJ IDEA Reformat 결과를 기본으로 한다.
- 공백을 추가해 필드나 대입문의 열을 수동 정렬하지 않는다.
- 한 줄 import를 사용하고 사용하지 않는 import를 남기지 않는다.
- 기존 파일을 수정하더라도 요청과 관계없는 전체 파일 재포맷은 하지 않는다.
- `.editorconfig` 또는 formatter가 도입되면 도구 설정을 이 문서보다 우선한다.

## 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| package | 소문자, 모듈·레이어 경계 표현 | `depth.finvibe.modules.wallet.domain` |
| class/interface | PascalCase | `WalletService`, `WalletRepository` |
| method/field | camelCase | `findByUserId`, `createdAt` |
| constant/enum value | UPPER_SNAKE_CASE | `WALLET_NOT_FOUND` |
| input port | 역할 + `UseCase` | `WalletCommandUseCase` |
| output persistence port | 역할 + `Repository` | `WalletRepository` |
| persistence adapter | 역할 + `RepositoryImpl` | `WalletRepositoryImpl` |
| Spring Data repository | 역할 + `JpaRepository` | `WalletJpaRepository` |
| messaging adapter | 역할 + `Consumer`/`Producer` | `WalletKafkaConsumer` |

짧다는 이유만으로 의미가 불분명한 약어를 만들지 않는다. 기존 domain 용어를 우선 사용한다.

## Domain 코드

- 비즈니스 규칙과 불변식은 domain에 둔다.
- Value Object는 가능한 한 불변으로 만들고 연산 결과를 새 인스턴스로 반환한다.
- Entity의 상태 변경은 의미 있는 메서드로 표현한다.
- 생성 의도가 필요한 Entity는 `create`, `of` 등 정적 팩토리를 사용할 수 있다.
- 비즈니스 규칙 위반은 `DomainException`과 모듈의 `*ErrorCode`로 표현한다.
- HTTP status, Controller DTO, Kafka, Redis 등 기술·전송 타입을 domain에 넣지 않는다.
- JPA Entity를 domain model로 함께 사용하는 것은 허용한다.

예시:

```java
public Money minus(Money other) {
    if (price < other.price) {
        throw new DomainException(WalletErrorCode.INSUFFICIENT_BALANCE);
    }
    return new Money(price - other.price);
}
```

## Application 코드

- 유스케이스 조립, 트랜잭션 경계와 orchestration을 담당한다.
- 트랜잭션은 application service의 유스케이스 경계에 둔다.
- 조회 전용 유스케이스는 필요하면 `@Transactional(readOnly = true)`를 사용한다.
- 입력의 비즈니스 유효성을 빠르게 검사하고 `DomainException`으로 표현한다.
- DB, Redis, Kafka, 외부 API와 SDK는 output port를 통해 사용한다.
- Spring Data repository, Redis repository 구현과 외부 client 구현을 직접 import하지 않는다.
- domain Entity를 API 계층에 그대로 반환하지 않고 DTO로 변환한다.

Application에서 다음 프레임워크 사용은 허용한다.

- `@Service`, `@Component`
- `@Transactional`
- SLF4J logging
- Micrometer metric

관측 코드가 핵심 흐름을 가리거나 단위 테스트를 어렵게 만들면 별도 component 또는 port로 추출한다.

## DTO와 계약

- 모듈 공용 DTO는 `modules/<module>/dto`에 둔다.
- HTTP transport에만 필요한 request/response는 `api/external` 또는 `api/internal` 가까이에 둘 수 있다.
- domain에서 DTO로 변환할 때 DTO의 `from` 정적 팩토리를 기본으로 한다.
- event payload처럼 값 전달이 목적인 타입은 `record`를 사용할 수 있다.
- API와 application port에서 JPA Entity를 직접 반환하지 않는다.
- 한 모듈의 application port가 다른 모듈의 DTO를 반환하지 않는다.

예시:

```java
public static WalletResponse from(Wallet wallet) {
    return WalletResponse.builder()
        .walletId(wallet.getId())
        .userId(wallet.getUserId())
        .balance(wallet.getBalance().getPrice())
        .build();
}
```

## Infra 코드

### Persistence

- Spring Data interface는 `infra/persistence/*JpaRepository`에 둔다.
- application output port 구현은 `infra/persistence/*RepositoryImpl`에 둔다.
- persistence adapter가 JPA query, QueryDSL과 DB 세부사항을 캡슐화한다.
- application에 Spring Data 타입을 노출하지 않는다.

### Messaging

- Consumer는 수신, 역직렬화된 계약 추출, input port 호출까지만 담당한다.
- 비즈니스 규칙을 Consumer에 작성하지 않는다.
- Producer는 topic, key, serialization 등 전송 책임을 캡슐화한다.
- event DTO는 domain Entity와 분리한다.
- topic과 group ID는 domain, event와 version을 알아볼 수 있게 정한다.

### External client

- 외부 HTTP API·SDK 구현은 `infra/client`에 둔다.
- timeout, retry, circuit breaker와 protocol mapping을 adapter가 처리한다.
- 외부 응답 객체를 application/domain에 그대로 노출하지 않는다.

## Lombok

- 생성자 주입은 `@RequiredArgsConstructor`를 기본으로 한다.
- 로깅은 `@Slf4j`를 사용한다.
- DTO는 필요한 경우 `@Builder`를 사용한다.
- getter와 setter는 필요한 범위만 노출한다.
- `@Data`는 DTO에만 제한적으로 허용한다.
- Entity와 Value Object에 `@Data`를 사용하지 않는다.

## 메서드 작성

- 한 메서드가 한 수준의 책임을 갖게 한다.
- 조건과 의도를 메서드 이름으로 표현할 수 있으면 설명 주석 대신 메서드 추출을 우선한다.
- boolean 인자로 여러 동작을 전환하는 메서드를 지양한다.
- 불가능한 상태를 숨기는 null 반환보다 명시적인 결과 또는 예외를 사용한다.
- 같은 로직이 한 번만 사용된다는 이유로 불필요한 범용 abstraction을 만들지 않는다.

## 테스트

JUnit 5, AssertJ와 Mockito를 기본으로 사용한다.

### 위치와 이름

- 테스트는 대상 클래스와 같은 package에 둔다.
- 테스트 클래스는 `*Test`로 끝낸다.
- 테스트 메서드는 `대상_조건_결과`가 드러나는 이름을 사용한다.
- `@DisplayName`은 행위 중심의 한국어 문장으로 작성한다.

예시:

```java
@Test
@DisplayName("잔액이 부족하면 출금에 실패한다")
void withdraw_insufficientBalance_fails() {
    // given

    // when & then
}
```

### 구조와 검증

- 준비·실행·검증을 `// given`, `// when`, `// then`으로 구분한다.
- 예외 검증은 예외 타입뿐 아니라 `DomainException.errorCode`까지 확인한다.
- mock interaction보다 결과와 상태 검증을 우선하되, adapter 호출이 계약이면 interaction을 검증한다.
- 테스트 간 상태와 실행 순서에 의존하지 않는다.
- 현재 시각과 UUID 등 비결정적 값은 테스트에서 제어한다.
- trivial context load처럼 구분할 준비·실행 단계가 없는 테스트에 빈 주석을 강제하지 않는다.

## 검증 명령

변경 범위에 맞는 가장 작은 검증부터 실행하고 마지막에 필요한 전체 검증을 수행한다.

```bash
./gradlew test --tests "<완전한 테스트 클래스명>"
./gradlew compileJava
./gradlew test
```

설정·패키징까지 영향을 주는 변경은 필요하면 `./gradlew clean build`로 확인한다.
