# 수익률 Redis-MariaDB Fallback Decisions

## 결정 상태

- 사용자는 2026-08-29 구현 계획에서 D1~D4의 1번을 명시적으로 승인했다.

## D1. fallback 단위

- 선택: 사용자와 포트폴리오별 항목 단위 Redis → MariaDB fallback
- 결정자: 사용자
- 이유: Redis에 남은 최신 항목을 보존하면서 miss 항목만 복구한다.
- 트레이드오프: 조합 로직과 항목별 source 관측이 전체 fallback보다 복잡하다.

## D2. 양쪽에 값이 없는 경우

- 선택: 0 값과 `updatedAt=null` 반환
- 결정자: 사용자
- 이유: 신규 사용자와 빈 포트폴리오를 오류가 아닌 정상 상태로 표현한다.
- 트레이드오프: 실제 0과 아직 valuation이 생성되지 않은 상태는 `updatedAt`으로 구분해야 한다.

## D3. cache refill 동시성

- 선택: Redis 단일 key CAS로 DB가 더 최신일 때만 refill
- 결정자: 사용자
- 이유: refill 직전 Worker가 쓴 값을 덮어쓰지 않고 DB 부하를 줄인다.
- 트레이드오프: Lua CAS와 필드 검증 코드가 추가된다.
- 구현 보완: 기존 key에는 조회 필드만 갱신하고 Worker 계산용 `cvp`는 건드리지 않는다. key가 없을 때만 DB `cv`로 `cvp`를 초기화한다.

## D4. 공개 API 위치

- 선택: `GET /portfolios/valuations` 전용 API
- 결정자: 사용자
- 이유: 기존 `/portfolios`, `/members/me` 응답 계약과 책임을 유지한다.
- 트레이드오프: 클라이언트가 수익률 화면에서 별도 API를 호출해야 한다.
