# 수익률 Redis-MariaDB Fallback Overview

## 연결 Issue

- Issue: [#14 feat: 수익률 Redis-MariaDB fallback 조회 API](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/14)

## 작업 배경과 문제

- 최신 포트폴리오·사용자 수익률이 Redis에만 있으면 Redis 장애 동안 조회할 수 없다.
- Batch가 MariaDB에 최대 약 30초 지연된 최신 스냅샷을 저장하므로 조회 API가 이를 장애 fallback으로 사용해야 한다.
- 기존 `/portfolios`와 `/members/me` 응답은 변경할 수 없다.

## 현재 구현과 확인된 사실

- `PortfolioController`에는 포트폴리오 그룹 CRUD만 있고 valuation 전용 조회는 없다.
- 인증 사용자 ID는 `Long`이며 Redis와 `user_valuation.user_id`는 문자열 계약을 사용한다.
- 포트폴리오 소유권 목록은 `PortfolioGroupRepository.findAllByUserId`로 조회할 수 있다.
- Redis valuation hash는 `pf:{portfolioId}`, `usr:{userId}`이고 공통 필드는 `pv`, `cv`, `pr`, `ua`이다.
- 포트폴리오는 `ac`, 사용자는 `pc`, 삭제 포트폴리오는 `del=1`을 추가로 사용한다.
- MariaDB 최신 스냅샷 테이블은 `portfolio_valuation`, `user_valuation`이다.

## 해결해야 하는 문제

1. 사용자 합계와 소유 포트폴리오별 최신 수익률을 한 번에 반환한다.
2. Redis 연결 실패, 키 부재, 필수 필드 누락을 항목별 MariaDB fallback으로 처리한다.
3. 양쪽에 값이 없는 항목은 0 값과 `updatedAt=null`로 반환한다.
4. DB 스냅샷을 Redis에 채울 때 더 최신인 Worker 값을 덮어쓰지 않는다.
5. 기존 공개 API 계약을 유지한다.

## 변경 범위

- `GET /portfolios/valuations`
- asset 모듈 valuation 조회 use case, service, Redis·JPA adapter
- 전용 응답 DTO와 read-only persistence model
- Redis hit, 부분·전체 miss, 연결 실패, 빈 값, cache 역전 방지 테스트

## 제외 범위

- 기존 `/portfolios`, `/members/me` 응답 변경
- valuation 쓰기, Kafka consumer와 Batch scheduler 변경
- 수익률 이력 및 일일 snapshot 변경
- 신규 테이블 또는 분산 트랜잭션 추가

## 위험과 제약

- Redis 전체 장애 중에는 API 요청마다 MariaDB 읽기가 발생한다.
- DB fallback은 최대 약 30초 오래될 수 있으므로 응답의 `updatedAt`을 유지한다.
- cache refill과 Worker 갱신이 경합할 수 있어 Redis 단일 키 CAS가 필요하다.
- 배포 순서상 valuation 테이블이 없는 환경에서는 DB fallback을 사용할 수 없다.

## 승인 내용

- 사용자는 2026-08-29 Redis 우선·MariaDB 항목별 fallback과 최대 약 30초 지연을 승인했다.
- 사용자는 별도 API 추가, 0 값 기본 응답, 최신 Worker 값 보호와 기존 API 계약 유지를 승인했다.
