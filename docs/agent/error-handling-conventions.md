# 오류 처리 컨벤션

예외, error code, HTTP status, `ErrorResponse` 또는 모듈별 error mapper를 변경할 때만 이 문서를 읽는다.

## 원칙

- domain과 application은 HTTP status와 응답 형식을 모른다.
- 비즈니스 규칙 위반은 `DomainException`으로 통일한다.
- 오류 원인은 모듈의 `*ErrorCode`로 식별한다.
- HTTP 변환은 전역 예외 처리기와 모듈별 mapper가 담당한다.
- 외부 계약의 안정적인 식별자는 사람이 읽는 message가 아니라 `code`다.

## 현재 공통 계약

### `DomainErrorCode`

위치: `common/error/DomainErrorCode`

```java
public interface DomainErrorCode {
    String getCode();
    String getMessage();
}
```

- `code`: 클라이언트와 로그가 사용하는 안정적인 오류 식별자
- `message`: 현재 API가 반환하는 설명

현재 계약에는 `messageKey`가 없다. i18n 전환은 외부 API 호환성을 포함한 별도 작업으로 다룬다.

### `DomainException`

위치: `common/error/DomainException`

- 비즈니스 규칙 위반의 공통 `RuntimeException`이다.
- 내부에 `DomainErrorCode errorCode`를 가진다.
- domain과 application은 이 예외를 다른 HTTP·기술 예외로 변환하지 않는다.

### `ErrorResponse`

위치: `common/infra/error/ErrorResponse`

현재 응답 필드는 다음과 같다.

```text
status
code
message
fieldErrors  # 선택
```

문서 작업만으로 필드 이름과 형식을 바꾸지 않는다.

### `DomainErrorHttpMapper`

위치: `common/infra/error/DomainErrorHttpMapper`

- 모듈 ErrorCode 지원 여부를 `supports`로 판단한다.
- 지원하는 ErrorCode를 `HttpStatusCode`로 변환한다.
- 모듈별 구현은 `modules/<module>/infra/error/*ErrorHttpMapper`에 둔다.

### `GlobalExceptionHandler`

위치: `common/infra/error/GlobalExceptionHandler`

- `DomainException`을 catch한다.
- 등록된 mapper 중 ErrorCode를 지원하는 mapper를 찾는다.
- mapper가 없으면 `500 Internal Server Error`를 반환한다.
- `ErrorResponse(status, code, message)`를 생성한다.
- validation, 지원하지 않는 method/media type과 예상하지 못한 예외를 공통 처리한다.

## 표준 흐름

```text
domain/application
    └─ throw DomainException(ModuleErrorCode)
          ↓
GlobalExceptionHandler
    └─ DomainErrorHttpMapper.supports(errorCode)
          ↓
ModuleErrorHttpMapper.toStatus(errorCode)
          ↓
ErrorResponse(status, code, message, fieldErrors?)
```

## 계층별 책임

### Domain

- 불변식과 비즈니스 규칙 위반 시 `DomainException`을 발생시킨다.
- 모듈의 `domain/error/<Module>ErrorCode`를 사용한다.
- HTTP, Kafka, Redis와 Spring Web 예외를 사용하지 않는다.

### Application

- 유스케이스 입력과 비즈니스 상태를 검증한다.
- 비즈니스 오류는 `DomainException`으로 표현한다.
- domain에서 발생한 `DomainException`을 HTTP 예외로 변환하지 않고 그대로 전파한다.
- 복구하거나 의미를 추가하지 않는 catch/rethrow를 작성하지 않는다.

프로그래밍 오류와 복구 불가능한 기술 환경 오류까지 억지로 `DomainException`으로 바꾸지 않는다. 전역 예상치 못한 예외 처리와 logging을 사용한다.

### API와 Infra

- Controller가 domain 오류를 개별적으로 HTTP 응답으로 변환하지 않는다.
- 모듈별 HTTP status 정책은 `infra/error/*ErrorHttpMapper`에 둔다.
- 외부 API·messaging 오류를 domain 의미로 변환할 필요가 있으면 adapter 경계에서 명시적으로 매핑한다.

## 모듈 ErrorCode 추가 절차

### 1. ErrorCode 정의

```text
modules/<module>/domain/error/<Module>ErrorCode.java
```

- enum으로 정의하고 `DomainErrorCode`를 구현한다.
- `code`는 모듈 안에서 중복되지 않는 `UPPER_SNAKE_CASE`를 사용한다.
- `message`는 현재 API 계약에 맞는 설명을 제공한다.

### 2. 규칙 위반 지점에서 발생

```java
throw new DomainException(WalletErrorCode.INSUFFICIENT_BALANCE);
```

ErrorCode 없이 문자열만 담은 `IllegalArgumentException` 또는 `IllegalStateException`으로 비즈니스 오류를 표현하지 않는다.

### 3. HTTP mapper 추가 또는 갱신

```text
modules/<module>/infra/error/<Module>ErrorHttpMapper.java
```

```java
@Component
public class WalletErrorHttpMapper implements DomainErrorHttpMapper {
    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof WalletErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        return switch ((WalletErrorCode) code) {
            case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_USER_ID, INVALID_MONEY_PRICE, INSUFFICIENT_BALANCE -> HttpStatus.BAD_REQUEST;
        };
    }
}
```

새 ErrorCode를 추가할 때 mapper의 switch도 반드시 함께 갱신한다. mapper 누락은 의도하지 않은 500 응답을 만든다.

## HTTP status 기준

| 상태 | 사용 기준 |
|---|---|
| `400 BAD_REQUEST` | 입력값이 유효하지 않거나 요청 수정으로 해결 가능한 비즈니스 규칙 위반 |
| `401 UNAUTHORIZED` | 인증 정보가 없거나 유효하지 않음 |
| `403 FORBIDDEN` | 인증됐지만 해당 작업 권한이 없음 |
| `404 NOT_FOUND` | 요청한 단일 리소스가 존재하지 않음 |
| `409 CONFLICT` | 현재 리소스 상태와 요청이 충돌함 |
| `500 INTERNAL_SERVER_ERROR` | mapper 누락, 예상하지 못한 오류, 서버 환경 문제 |

도메인 이름만 보고 기계적으로 status를 정하지 않는다. 클라이언트가 오류를 어떻게 처리해야 하는지를 기준으로 선택한다.

## Validation 오류

- transport 형식 검증은 Bean Validation과 API 계층에서 처리할 수 있다.
- `MethodArgumentNotValidException`, `ConstraintViolationException` 등은 `GlobalExceptionHandler`가 `fieldErrors`로 변환한다.
- 비즈니스 의미가 있는 유효성은 application/domain에서 `DomainException`으로 검증한다.
- 같은 규칙을 Controller와 domain에 중복 구현하지 않는다.

## 테스트

### Domain/Application

- `DomainException` 타입을 확인한다.
- `getErrorCode()`가 기대한 모듈 ErrorCode인지 확인한다.

```java
assertThatThrownBy(() -> wallet.withdraw(amount))
    .isInstanceOf(DomainException.class)
    .extracting(error -> ((DomainException) error).getErrorCode())
    .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);
```

### Mapper

- 모듈 ErrorCode 전체에 대해 `supports`가 true인지 검증한다.
- 각 ErrorCode가 기대한 HTTP status로 매핑되는지 검증한다.

### API/Integration

- `DomainException`이 예상 `status`, `code`, `message`로 변환되는지 검증한다.
- validation 오류의 `fieldErrors` 포함 여부를 검증한다.
- 새 ErrorCode가 500 fallback으로 빠지지 않는지 확인한다.

## Legacy 처리

현재 일부 모듈은 ErrorCode가 있지만 전용 HTTP mapper가 없거나 application에서 일반 예외로 비즈니스 상태를 표현한다.

- 새 오류를 추가할 때 기존 누락을 확대하지 않는다.
- 요청 범위에 포함된 legacy 오류는 이 문서 기준으로 정리한다.
- 다른 모듈의 오류까지 한 변경에 섞지 않는다.
- 전체 누락 정리는 별도 task로 계획한다.

## 변경 체크리스트

```text
[ ] 비즈니스 오류가 DomainException을 사용하는가?
[ ] 모듈 ErrorCode에 안정적인 code와 message가 있는가?
[ ] 모듈 ErrorHttpMapper가 새 ErrorCode를 처리하는가?
[ ] HTTP status가 클라이언트 처리 방식과 맞는가?
[ ] ErrorResponse의 기존 필드를 임의로 바꾸지 않았는가?
[ ] 테스트가 예외 타입과 errorCode를 함께 검증하는가?
[ ] API 테스트에서 의도하지 않은 500 fallback이 없는가?
```
