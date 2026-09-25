# 수익률 Redis-MariaDB Fallback Plan

## 기준

- Overview: `docs/tasks/portfolio-valuation-fallback/01-overview.md`
- Issue: [#14](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/14)

## 완료 조건

1. `GET /portfolios/valuations`가 사용자 합계와 소유 포트폴리오 목록을 반환한다.
2. Redis의 모든 필수 필드가 유효하면 Redis 값을 사용한다.
3. 일부·전체 miss와 Redis 연결 실패 시 해당 항목만 MariaDB로 fallback한다.
4. 양쪽에 값이 없으면 금액·수익률·개수는 0이고 `updatedAt`은 null이다.
5. DB cache refill은 Redis보다 최신인 경우에만 성공하며 Worker 갱신과 경합해도 역전되지 않는다.
6. 기존 `/portfolios`, `/members/me` 계약은 바뀌지 않는다.
7. 관련 테스트와 전체 테스트가 통과한다.

## 구현 단계

### 1. 조회 계약과 port

- 전용 응답 DTO와 query use case를 추가한다.
- Redis cache와 MariaDB snapshot을 동일한 valuation snapshot 계약으로 읽는다.
- `Long` 인증 ID는 Redis·DB 조회 시 문자열로 변환한다.

검증: 응답 직렬화와 ID 변환을 단위 테스트한다.

### 2. Redis·MariaDB adapter

- Redis hash의 필수 필드를 검증하고 하나라도 없거나 파싱할 수 없으면 miss로 처리한다.
- JPA read model은 활성 포트폴리오와 사용자 최신 1행만 조회한다.
- cache refill은 단일 Redis key Lua CAS로 현재 `updatedAt`을 재확인한다.

검증: hit, 필드 누락, malformed 값, 삭제 표시, DB 조회와 CAS 경합을 테스트한다.

### 3. 항목별 fallback 조합

- 소유 포트폴리오 ID를 기준으로 응답 순서를 결정한다.
- 사용자와 각 포트폴리오를 Redis → DB → zero 순으로 조합한다.
- DB를 사용한 항목은 응답을 막지 않는 best-effort cache refill을 수행한다.

검증: 일부 miss, 전체 miss, Redis 예외, DB miss와 빈 사용자 시나리오를 테스트한다.

### 4. API와 전체 검증

- `PortfolioController`에 `/valuations`를 추가하고 기존 메서드는 수정하지 않는다.
- controller, service, adapter 테스트 후 전체 Gradle 테스트를 수행한다.

## 결정 포인트

### D1. fallback 단위

1. 항목별 fallback: 가용성과 Redis 최신 값을 함께 보존한다.
2. 하나라도 miss면 전체 DB: 단순하지만 최신 Redis 값도 버린다.
3. Redis 실패를 API 실패로 반환: 장애 가용성 목표를 충족하지 못한다.

추천·승인: 1번.

### D2. 양쪽에 값이 없는 경우

1. 0 값과 null 시각 반환: 신규·빈 상태를 정상 응답으로 표현한다.
2. 항목 제외: 소유 포트폴리오 목록과 valuation 목록이 어긋난다.
3. 404 반환: 부분 miss를 전체 실패로 확대한다.

추천·승인: 1번.

### D3. cache refill 동시성

1. 단일 key CAS: Worker 갱신을 덮어쓰지 않으면서 복구한다.
2. 일반 HSET: 읽기와 쓰기 사이 경합으로 최신 값이 역전될 수 있다.
3. refill 없음: 안전하지만 복구 후 DB 부하가 지속된다.

추천·승인: 1번.

### D4. 공개 API 위치

1. 별도 `/portfolios/valuations`: 기존 계약을 보존한다.
2. `/portfolios` 확장: 기존 클라이언트 계약이 바뀐다.
3. `/members/me` 확장: asset 상세와 user 프로필 책임이 섞인다.

추천·승인: 1번.

## 적용 순서

1. Batch와 Worker가 valuation 필드·테이블을 먼저 제공한다.
2. Monolith API를 배포한다.
3. Redis 중단 검증으로 DB `updatedAt` 지연과 HTTP 성공을 확인한다.
