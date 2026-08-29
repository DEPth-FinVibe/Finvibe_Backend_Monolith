# 수익률 Redis-MariaDB Fallback Result

## 연결 항목

- Issue: [#14](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/14)
- Branch: `feat/14-portfolio-valuation-fallback`
- Overview: `docs/tasks/portfolio-valuation-fallback/01-overview.md`
- Plan: `docs/tasks/portfolio-valuation-fallback/02-plan.md`
- Decisions: `docs/tasks/portfolio-valuation-fallback/03-decisions.md`

## 구현 결과

### 공개 API

- `GET /portfolios/valuations`를 추가했다.
- 응답은 사용자 합계와 소유 포트폴리오별 `purchasedValue`, `currentValue`, `profitRate`, 개수와 `updatedAt`을 포함한다.
- 기존 `/portfolios`, `/members/me` 코드는 변경하지 않았다.

### 항목별 fallback

- 포트폴리오 소유 목록을 기준으로 Redis → MariaDB → zero 순서로 각 항목을 조합한다.
- Redis 연결 예외는 API 실패로 전파하지 않고 MariaDB 조회로 전환한다.
- 키 부재, 필수 필드 누락, malformed 값과 `del=1` 포트폴리오는 Redis miss로 처리한다.
- 양쪽에 값이 없으면 금액·수익률·자산 수는 0이고 `updatedAt`은 null이다.
- 사용자 valuation이 없을 때 `portfolioCount`는 실제 소유 포트폴리오 수를 사용한다.

### read model과 cache refill

- `portfolio_valuation`, `user_valuation`을 읽는 immutable JPA model과 repository를 추가했다.
- 인증 `Long` ID는 기존 Redis·DB 문자열 계약에 맞춰 변환한다.
- DB fallback cache refill은 현재 Redis `updatedAt`을 읽고 단일 key Lua CAS로 다시 확인한다.
- 기존 key의 Worker 계산 필드 `cvp`는 수정하지 않고, key가 없을 때만 DB `currentValue`로 초기화한다.
- 삭제 tombstone과 더 최신이거나 시각이 잘못된 Redis 값은 refill하지 않는다.
- refill 실패는 응답을 막지 않는다.

## 관측 항목

- `asset.valuation.read{type=user|portfolio,source=redis|database|zero}`
- `asset.valuation.cache.failure{operation=user|portfolio|refill}`

## 검증 결과

통과:

```bash
./gradlew compileJava
./gradlew test --tests depth.finvibe.modules.asset.application.PortfolioValuationQueryServiceTest \
  --tests depth.finvibe.modules.asset.infra.redis.ValuationCacheRepositoryImplTest \
  --tests depth.finvibe.modules.asset.api.external.PortfolioControllerValuationTest
./gradlew test
```

검증한 시나리오:

- Redis 전체 hit와 DB 미조회
- 포트폴리오 일부 miss의 항목별 DB fallback과 응답 순서
- Redis 연결 예외 시 사용자·포트폴리오 DB fallback
- 양쪽 miss의 zero 응답과 null 시각
- 필수 필드 누락, malformed 시각과 삭제 cache miss
- DB보다 최신인 Redis 값의 refill 차단
- Redis refill 실패 중 DB 응답 유지

## 운영 영향과 배포 순서

1. Worker와 Batch를 먼저 배포해 Redis 필드와 MariaDB 스냅샷을 생성한다.
2. `portfolio_valuation`, `user_valuation` 테이블이 있는지 확인한다.
3. Monolith API를 배포한다. remote profile은 `ddl-auto=validate`이므로 테이블이 먼저 필요하다.
4. 정상 상태에서 dirty backlog가 다음 30초 주기 안에 0이 되는지 확인한다.
5. Redis 중단 후 API HTTP 성공, DB 값과 `updatedAt` 지연을 확인한다.

## 남은 검증과 한계

- 로컬 MariaDB와 실행 중인 Docker가 없어 실제 Redis 중단 + MariaDB 조회의 end-to-end 검증은 수행하지 못했다.
- 따라서 장애 직전 대비 `updatedAt`이 30초 이내인지 스테이징에서 확인해야 한다.
- Redis 전체 장애 중에는 사용자별 API 요청이 MariaDB 읽기로 전환되므로 DB 부하를 관찰해야 한다.

## 롤백

- Monolith API 커밋을 되돌리면 기존 공개 API에는 영향 없이 전용 endpoint만 제거된다.
- Worker와 Batch를 먼저 롤백하지 않으면 기존 valuation snapshot 적재는 계속되며 기능상 문제는 없다.
