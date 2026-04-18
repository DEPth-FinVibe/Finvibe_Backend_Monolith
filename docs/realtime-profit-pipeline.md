# 실시간 수익률 갱신 파이프라인 설계 문서

## 1. 배경 및 문제

### 현재 구조
수익률 갱신이 1시간 배치 주기(`Batch → Kafka → Monolith.recalculateAllProfits()`)로 동작.

### 문제점 (10만 MAU 기준)
| 병목 지점 | 설명 | 메모리 영향 |
|-----------|------|------------|
| `readAllUserProfitSummaries()` | 전체 유저 대상 GROUP BY 스캔 | ~11MB |
| `getUserNamesByIds(100K)` | 10만건 외부 API 호출 | ~100MB+ |
| 3-4개 동시 100K 컬렉션 | rankings, summaries, userIds, nicknames | ~300-500MB |
| `replaceAllRankings()` | DELETE ALL + INSERT ALL × 3 타입 = 30만 JPA 엔티티 | OOM 직접 원인 |

**핵심 원인**: K(종목) × M(유저) × N(포트폴리오) fan-out을 단일 트랜잭션에서 처리

### 목표
- Redis 기반 비동기 파이프라인으로 전환
- 실시간 수익률 갱신 (가격 변동 즉시 반영)
- OOM 제거 + 지연 30초 이하

---

## 2. 현재 가격 이벤트 흐름

```
KIS WebSocket (증권사 API)
  → KisConnectionPool (Monolith)
    → SpringEvent: CurrentPriceUpdatedEvent
      → MarketEventConsumer (@EventListener @Async)
        → CurrentPriceService.stockPriceUpdated()
          → watcher 존재 확인
          → Redis 현재가 저장 (market:current-price:{stockId})
          → Redis Pub/Sub 발행 (market:price-updated)
            → WebSocket-Listener 구독 → 클라이언트 broadcast
```

Monolith가 KIS로부터 가격을 받아 Redis Pub/Sub로 발행하고, WebSocket-Listener가 이를 구독하여 클라이언트에게 전달.

---

## 3. 가격 이벤트 수신 방안 비교

Worker가 가격 변동 이벤트를 수신하는 두 가지 접근 방식을 비교한다.

### 방안 1: Worker가 Redis Pub/Sub 직접 구독

```
  ┌─────────┐     ┌───────────┐     ┌─────────────────┐
  │   KIS   │────>│ Monolith  │────>│  Redis Pub/Sub   │
  │WebSocket│     │           │     │ market:price-    │
  └─────────┘     └───────────┘     │   updated        │
                                    └────────┬─────────┘
                                             │
                              ┌──────────────┼──────────────┐
                              │              │              │
                              ▼              ▼              │
                    ┌──────────────┐  ┌────────────┐       │
                    │  WS-Listener │  │   Worker   │       │
                    │  (기존)       │  │  (신규)     │       │
                    │  클라이언트    │  │  수익률 계산 │       │
                    │  broadcast   │  │            │       │
                    └──────────────┘  └────────────┘       │
```

| 항목 | 평가 |
|------|------|
| 구현 복잡도 | **낮음** — Worker에 Redis subscriber 추가만 |
| 신규 인프라 | 없음 — 기존 채널 재사용 |
| 이벤트 유실 | Worker 다운 시 유실, 다음 이벤트로 보정 |
| 배압 처리 | 없음 — 빠르게 처리 못하면 drop |
| 서비스 간 결합도 | Worker가 Monolith의 Redis Pub/Sub 채널에 직접 의존 |
| Monolith 변경 | 불필요 |

**적합한 경우**: 수익률 계산은 최신 가격만 필요하므로 유실이 허용됨. 단순함 우선.

### 방안 2: Monolith에서 Kafka 이벤트 별도 발행

```
  ┌─────────┐     ┌───────────────────────┐
  │   KIS   │────>│       Monolith        │
  │WebSocket│     │                       │
  └─────────┘     │  CurrentPriceService  │
                  │   .stockPriceUpdated()│
                  └───┬───────────────┬───┘
                      │               │
              (기존)   │               │  (신규)
                      ▼               ▼
            ┌──────────────┐  ┌──────────────────────────┐
            │ Redis Pub/Sub│  │         Kafka             │
            │ market:price-│  │ market.stock-price-       │
            │   updated    │  │   updated.v1              │
            └──────┬───────┘  └────────────┬─────────────┘
                   │                       │
                   ▼                       ▼
            ┌──────────────┐       ┌────────────┐
            │ WS-Listener  │       │   Worker   │
            │ (시세 표시)    │       │ (수익률 계산)│
            └──────────────┘       └────────────┘
```

Monolith의 `CurrentPriceService.stockPriceUpdated()`에서 기존 Redis Pub/Sub 발행과 별도로 Kafka produce 추가.

| 항목 | 평가 |
|------|------|
| 구현 복잡도 | **중간** — Kafka producer + consumer + 토픽 추가 |
| 신규 인프라 | Kafka 토픽 1개 (`market.stock-price-updated.v1`) |
| 이벤트 유실 | Kafka 영속성으로 유실 방지 + replay 가능 |
| 배압 처리 | consumer lag으로 자연 처리 |
| 서비스 간 결합도 | Kafka 토픽 계약 기반 — 느슨한 결합 |
| Monolith 변경 | Kafka produce 1줄 추가 |

**적합한 경우**: 이벤트 목적이 다름(시세 표시 vs 수익률 계산) → 채널 분리가 아키텍처적으로 자연스러움.

### 방안 비교 요약

| 비교 항목 | 방안 1 (Redis Pub/Sub) | 방안 2 (Kafka 별도) |
|-----------|----------------------|-------------------|
| 구현 난이도 | 쉬움 | 보통 |
| 이벤트 유실 대응 | 다음 이벤트로 보정 | Kafka 영속성 |
| 배압 내성 | 약함 | 강함 |
| 관심사 분리 | 약함 (같은 채널 공유) | 강함 (목적별 채널) |
| 확장성 | subscriber 추가 시 부하 공유 | partition 기반 scale-out |
| 운영 모니터링 | Redis Pub/Sub 모니터링 어려움 | Kafka consumer lag 추적 용이 |

**두 방안 모두 유효**하며, 현재 구현은 **방안 2(Kafka)**를 기본으로 적용. 방안 1로 전환해도 Worker 내부 로직은 동일.

---

## 4. 아키텍처 개요

### 4-1. 전체 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              External                                           │
│                                                                                 │
│   ┌──────────────┐          ┌──────────────┐                                    │
│   │  KIS 증권사   │          │   클라이언트   │                                    │
│   │  WebSocket   │          │  (브라우저)    │                                    │
│   └──────┬───────┘          └──────▲───────┘                                    │
│          │ 실시간 시세                │ WebSocket                                  │
└──────────┼──────────────────────────┼───────────────────────────────────────────┘
           │                          │
┌──────────▼──────────────────────────┼───────────────────────────────────────────┐
│                          Services                                               │
│                                                                                 │
│  ┌─────────────────────────────┐    │    ┌──────────────────────────────┐        │
│  │        Monolith             │    │    │    WebSocket-Listener        │        │
│  │                             │    │    │                              │        │
│  │  ┌───────────────────────┐  │    │    │  Redis Pub/Sub 구독          │        │
│  │  │ CurrentPriceService   │──┼────┼───>│  → 클라이언트 broadcast      │        │
│  │  │  · Redis Pub/Sub 발행  │  │    │    └──────────────────────────────┘        │
│  │  │  · Kafka produce      │  │    │                                            │
│  │  └───────────────────────┘  │    │    ┌──────────────────────────────┐        │
│  │                             │    │    │      Profit Worker           │        │
│  │  ┌───────────────────────┐  │    │    │      (별도 서버, DB 없음)     │        │
│  │  │ AssetService          │  │    │    │                              │        │
│  │  │  · 매수/매도/이전      │  │    │    │  Kafka consume               │        │
│  │  │  · Spring Event 발행   │  │    │    │  → 수익률 계산               │        │
│  │  └───────────────────────┘  │    │    │  → Redis 랭킹/요약 갱신      │        │
│  │                             │    │    └──────────────────────────────┘        │
│  │  ┌───────────────────────┐  │    │                                            │
│  │  │ RedisIndexSyncService │  │    │    ┌──────────────────────────────┐        │
│  │  │  · AFTER_COMMIT 동기화 │  │    │    │         Batch               │        │
│  │  └───────────────────────┘  │    │    │                              │        │
│  └─────────────────────────────┘    │    │  · 일일 스냅샷               │        │
│                                     │    │  · 주간/월간 랭킹            │        │
│                                     │    │  · Reconciliation (10분)     │        │
│                                     │    └──────────────────────────────┘        │
└─────────────────────────────────────┼───────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────┼───────────────────────────────────────────┐
│                         Infrastructure                                          │
│                                                                                 │
│  ┌────────────────────────────┐  ┌──┴───────────────────┐  ┌────────────────┐   │
│  │          Kafka             │  │        Redis          │  │    MariaDB     │   │
│  │                            │  │                       │  │                │   │
│  │  market.stock-price-       │  │  stock:holding:*      │  │  asset         │   │
│  │    updated.v1              │  │  portfolio:assets:*   │  │  portfolio_    │   │
│  │  market.batch-price-       │  │  portfolio:owner:*    │  │    group       │   │
│  │    updated.v1              │  │  user:profit-ranking  │  │  user_profit_  │   │
│  │                            │  │  user:profit-summary  │  │    ranking     │   │
│  │                            │  │  market:price-updated │  │                │   │
│  └────────────────────────────┘  └───────────────────────┘  └────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 4-2. 서비스 간 데이터 흐름

```
                       읽기/쓰기 방향 정리

  Monolith ──write──> Redis (인덱스 3종)
  Monolith ──write──> Kafka (가격 이벤트)
  Monolith ──write──> Redis Pub/Sub (시세 broadcast)
  Monolith ──read───> MariaDB
  Monolith ──write──> MariaDB

  Worker   ──read───> Kafka (가격 이벤트)
  Worker   ──read───> Redis (인덱스 3종)
  Worker   ──write──> Redis (랭킹 + 요약)

  Batch    ──read───> MariaDB (스냅샷, 랭킹 계산)
  Batch    ──write──> MariaDB (스냅샷, 랭킹)
  Batch    ──read───> Redis (reconciliation 비교)
  Batch    ──write──> Redis (reconciliation 보정, 랭킹 동기화)

  WS-Listener ──read──> Redis Pub/Sub (시세 구독)
```

---

## 5. Redis 데이터 모델

### 키 구조

| Redis 키 | 타입 | 용도 |
|-----------|------|------|
| `stock:holding:{stockId}:portfolios` | SET | 종목 → 포트폴리오 역방향 인덱스 |
| `portfolio:assets:{portfolioId}` | HASH | 포트폴리오 보유 자산 스냅샷 |
| `portfolio:owner:{portfolioId}` | STRING | 포트폴리오 → 유저 매핑 |
| `user:profit-ranking:{daily\|weekly\|monthly}` | ZSET | 수익률 랭킹 |
| `user:profit-summary:{userId}` | HASH | API 응답용 수익률 캐시 |

### 데이터 관계도

```
  종목(Stock)                포트폴리오(Portfolio)            유저(User)
  ──────────                ────────────────────            ──────────

  stock:holding:            portfolio:assets:               user:profit-
    {stockId}:                {portfolioId}                   summary:
    portfolios                                                {userId}
  ┌──────────┐            ┌──────────────────┐            ┌──────────────┐
  │   SET    │            │      HASH        │            │     HASH     │
  │          │  1:N       │                  │            │              │
  │ members: ├───────────>│ field: stockId   │  N:1       │ tcv, tpl,    │
  │ portfolio│            │ value: amount|   ├───────────>│ rr, at       │
  │   Ids    │            │   price|currency │  portfolio │              │
  │          │            │                  │  :owner:   └──────┬───────┘
  └──────────┘            └────────┬─────────┘  {id}             │
                                   │                             │
  "이 종목을 보유한               │            ┌──────────┐     │
   포트폴리오 목록"           ┌────▼─────┐     │ STRING   │     │
                             │portfolio │     │          │     │
                             │:owner:   ├────>│ value:   │     │
                             │{id}      │     │ userId   │     ▼
                             └──────────┘     └──────────┘  user:profit-
                                                              ranking:
                             "이 포트폴리오의                   {type}
                              소유자"                       ┌──────────┐
                                                            │   ZSET   │
                                                            │          │
                                                            │ member:  │
                                                            │  userId  │
                                                            │ score:   │
                                                            │  return  │
                                                            │  Rate    │
                                                            └──────────┘

  조회 경로 (Worker 수익률 계산):
  ─────────────────────────────
  stockId ──> stock:holding:{stockId}:portfolios  (어떤 포트폴리오가 보유?)
          ──> portfolio:assets:{portfolioId}       (보유 수량/매입가는?)
          ──> portfolio:owner:{portfolioId}         (소유자는?)
          ──> 수익률 계산 후
          ──> user:profit-summary:{userId}          (결과 저장)
          ──> user:profit-ranking:daily             (랭킹 갱신)
```

### Listpack 최적화

Redis 7.0+에서 ziplist → listpack으로 대체:
- cascading realloc 문제 해결
- 동일 데이터에서 ~10-20% 추가 메모리 절약
- 설정: `hash-max-listpack-entries` (기본 128), `hash-max-listpack-value` (기본 64 bytes)

**핵심**: `portfolio:assets` 값을 JSON이 아닌 파이프 구분 압축 포맷으로 저장하여 listpack 인코딩 유지.

```
# JSON (70 bytes → listpack 탈락, hashtable로 fallback)
{"amount":"10.5","purchasePriceAmount":"150000","currency":"KRW"}

# 압축 포맷 (20 bytes → listpack 유지, 메모리 ~40% 절약)
10.5|150000|KRW
```

### 메모리 추정 (10만 MAU, 50만 포트폴리오)

| Redis 키 | 인코딩 | 총 메모리 |
|-----------|--------|----------|
| `portfolio:assets:{id}` (50만개) | Listpack | ~250MB |
| `portfolio:owner:{id}` (50만개) | String | ~53MB |
| `stock:holding:{id}:portfolios` (500종목) | 혼합 | ~4MB |
| `user:profit-ranking:{type}` (3개) | Skiplist | ~24MB |
| `user:profit-summary:{id}` (10만개) | Listpack | ~20MB |
| **합계 (fragmentation 1.5배)** | | **~525MB** |

단일 Redis 4GB 노드로 충분.

---

## 6. 구현 상세

### Phase 1: Redis 인프라 레이어

#### 1-1. Redis Repository (Monolith)

5개 신규 Repository 생성:

| 파일 | 키 패턴 | 주요 메서드 |
|------|---------|------------|
| `StockHoldingIndexRedisRepository` | `stock:holding:{stockId}:portfolios` | SADD, SREM, SMEMBERS |
| `PortfolioAssetSnapshotRedisRepository` | `portfolio:assets:{portfolioId}` | HSET, HDEL, HGETALL |
| `PortfolioOwnerRedisRepository` | `portfolio:owner:{portfolioId}` | SET, GET, DEL |
| `UserProfitSummaryRedisRepository` | `user:profit-summary:{userId}` | HMSET, HGETALL |
| `UserProfitRankingRedisRepository` | `user:profit-ranking:{type}` | ZADD, ZREVRANGE, ZREVRANK |

#### 1-2. 매수/매도 시 Redis 인덱스 갱신

`AssetService`의 매수/매도/이전/생성/삭제 메서드에서 Spring Event 발행:

| 이벤트 | 발행 시점 | Redis 갱신 대상 |
|--------|----------|----------------|
| `AssetRegisteredEvent` | 매수 후 | stock:holding, portfolio:assets, portfolio:owner |
| `AssetUnregisteredEvent` | 매도 후 | stock:holding (전량 시 SREM), portfolio:assets |
| `AssetTransferredEvent` | 이전 후 | source에서 제거, target에 추가 |
| `PortfolioCreatedEvent` | 포트폴리오 생성 후 | portfolio:owner |
| `PortfolioDeletedEvent` | 포트폴리오 삭제 후 | stock:holding 이동, 삭제된 키 정리 |

핸들러: `RedisIndexSyncService`
- `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`
- DB 커밋 후 비동기로 Redis 갱신
- 실패 시 로그 경고 (reconciliation에서 보정)

```
  매수 예시 (registerAsset)
  ─────────────────────────

  Client                AssetService              DB                RedisIndexSyncService         Redis
    │                       │                      │                        │                       │
    │── POST /assets ──────>│                      │                        │                       │
    │                       │── register() ───────>│                        │                       │
    │                       │                      │── INSERT asset         │                       │
    │                       │                      │── COMMIT ─────────────>│                       │
    │                       │                      │                        │                       │
    │                       │  publishEvent(       │              (AFTER_COMMIT, @Async)             │
    │                       │   AssetRegistered)   │                        │                       │
    │<── 200 OK ────────────│                      │                        │── SADD stock:holding  │
    │                       │                      │                        │── HSET portfolio:     │
    │                       │                      │                        │     assets            │
    │                       │                      │                        │── SET portfolio:owner │
    │                       │                      │                        │                       │

  매도 시: AssetUnregisteredEvent → 전량이면 SREM + HDEL, 일부면 HSET 갱신
  이전 시: AssetTransferredEvent  → source에서 제거 + target에 추가
  삭제 시: PortfolioDeletedEvent  → 자산을 기본 포트폴리오로 이동, 삭제된 키 정리
```

### Phase 2: 수익률 계산 파이프라인

#### 2-1. 가격 변동 이벤트 발행 (Monolith)

`CurrentPriceService.stockPriceUpdated()`에서 기존 Redis Pub/Sub 발행 외에 Kafka produce 추가:

```java
// 기존: Redis Pub/Sub → WebSocket-Listener
currentPriceEventPublisher.publish(priceUpdate);

// 신규: Kafka → Worker
stockPriceEventProducer.publishStockPriceUpdated(StockPriceUpdatedEvent.builder()
    .stockId(priceUpdate.getStockId())
    .price(priceUpdate.getClose())
    .updatedAt(priceUpdate.getAt())
    .build());
```

- Kafka 토픽: `market.stock-price-updated.v1`
- Key: stockId (동일 종목은 같은 파티션으로)
- 경량 DTO: stockId + close 가격 + 시간만 전달

#### 2-2. Worker 서비스 (별도 레포)

`Finvibe-Profit-Worker` — Spring Boot + Kafka + Redis, DB 불필요.

처리 흐름:
```
Kafka Consumer (market.stock-price-updated.v1)
  → 1초 윈도우 coalescing (같은 stockId는 마지막 가격만)
  → SMEMBERS stock:holding:{stockId}:portfolios
  → For each portfolioId:
      HGETALL portfolio:assets:{portfolioId}
      AssetValuation.calculate() → 종목별 수익률
      PortfolioValuation.aggregate() → 포트폴리오 수익률
  → GET portfolio:owner:{portfolioId} → userId
  → userId별 전체 포트폴리오 합산
  → ZADD user:profit-ranking:daily {returnRate} {userId}
  → HMSET user:profit-summary:{userId} {...}
```

Coalescing: `ConcurrentHashMap<Long, StockPriceUpdatedEvent>` + 1초 flush 스케줄러

```
  Worker 내부 처리 흐름
  ────────────────────

  Kafka                                            Redis
  ─────                                            ─────

  market.stock-price-     ┌────────────────────┐
    updated.v1            │ PriceCoalescing     │
       │                  │   Buffer            │
       │  stockId=1,      │                     │
       │   price=50000    │ { 1: 50000,         │
       ├─────────────────>│   3: 72000,         │
       │  stockId=3,      │   1: 50500 }        │     1초 경과
       │   price=72000    │                     │         │
       ├─────────────────>│  (같은 stockId는    │         │
       │  stockId=1,      │   마지막 가격만 유지) │         │
       │   price=50500    │                     │         │
       ├─────────────────>│                     │         ▼
                          └─────────┬───────────┘
                                    │ drainAll()
                                    ▼
                          ┌────────────────────┐
                          │  flush() 스케줄러   │
                          │                    │
                          │  for each stockId: │
                          │                    │         ┌─────────────────────┐
                          │  ① SMEMBERS ──────────────> │ stock:holding:      │
                          │     stock:holding  │         │   {stockId}:        │
                          │                    │ <────── │   portfolios        │
                          │     portfolioIds   │         └─────────────────────┘
                          │       = [10, 20]   │
                          │                    │         ┌─────────────────────┐
                          │  ② HGETALL ───────────────> │ portfolio:assets:   │
                          │     portfolio:     │         │   {portfolioId}     │
                          │     assets         │ <────── │                     │
                          │                    │         └─────────────────────┘
                          │  ③ 수익률 계산      │
                          │     ProfitCalc     │
                          │     .calculateAsset│
                          │                    │         ┌─────────────────────┐
                          │  ④ GET ────────────────────> │ portfolio:owner:    │
                          │     portfolio:owner│         │   {portfolioId}     │
                          │                    │ <────── │   → userId          │
                          │                    │         └─────────────────────┘
                          │  ⑤ 유저별 합산     │
                          │                    │
                          │  ⑥ ZADD ───────────────────> user:profit-ranking:daily
                          │  ⑦ HMSET ──────────────────> user:profit-summary:{userId}
                          │                    │
                          └────────────────────┘
```

#### 2-3. 랭킹 저장소 전환

`UserProfitRankingRepositoryImpl.replaceAllRankings()`에서 DB 저장과 함께 Redis ZSET에도 동기화:

```java
// 기존: DB만 저장
jpaRepository.saveAll(entities);

// 추가: Redis ZSET 동기화
rankingRedisRepository.replaceAll(rankType, redisEntries);
```

주간/월간 랭킹 계산 로직(`UserProfitRankingAggregationService`)은 기존 유지.

### Phase 4: 안전장치

#### Reconciliation 배치 (Batch 서비스)

- 10분 주기 실행 (`@Scheduled(fixedRate = 600_000)`)
- 500개 단위 청킹으로 DB 조회
- DB vs Redis 비교: `portfolio:owner`, `portfolio:assets`, `stock:holding`
- 불일치 시 Redis를 DB 기준으로 보정
- 메트릭 기록 (`reconciliation.runs`, `reconciliation.fixes`)

```
  Reconciliation 흐름 (10분 주기)
  ──────────────────────────────

  lastPortfolioId = null

  ┌─────────────────────────────────────────────────────────────────┐
  │ LOOP                                                            │
  │                                                                 │
  │  ┌──────────────────┐                                           │
  │  │ DB 조회 (500개)   │  SELECT portfolio_group                   │
  │  │ findPortfolioIds │  WHERE id > lastPortfolioId               │
  │  │   After(last,500)│  ORDER BY id ASC LIMIT 500                │
  │  └────────┬─────────┘                                           │
  │           │                                                     │
  │           ▼  portfolioIds 비어있으면 → LOOP 종료                  │
  │                                                                 │
  │  ┌──────────────────┐                                           │
  │  │ DB 조회 (자산포함) │  SELECT pg + assets                       │
  │  │ findAllWithAssets │  WHERE id IN (portfolioIds)               │
  │  │   ByIds          │  LEFT JOIN assets                         │
  │  └────────┬─────────┘                                           │
  │           │                                                     │
  │           ▼                                                     │
  │  ┌──────────────────────────────────────────────────┐           │
  │  │  for each portfolio:                             │           │
  │  │                                                  │           │
  │  │  ① portfolio:owner 비교                           │           │
  │  │     DB userId ≠ Redis userId → SET 덮어쓰기       │           │
  │  │                                                  │           │
  │  │  ② portfolio:assets 비교                          │           │
  │  │     DB snapshots ≠ Redis snapshots               │           │
  │  │     → DEL + HMSET 전체 교체                       │           │
  │  │                                                  │           │
  │  │  ③ stock:holding 역방향 인덱스 수집               │           │
  │  │     DB 기준 stockId → portfolioIds 매핑 구축      │           │
  │  └──────────────────────────────────────────────────┘           │
  │           │                                                     │
  │           ▼                                                     │
  │  ┌──────────────────────────────────────────────────┐           │
  │  │  stock:holding 비교                              │           │
  │  │    DB portfolioIds ≠ Redis portfolioIds          │           │
  │  │    → 누락된 portfolioId SADD 추가                 │           │
  │  └──────────────────────────────────────────────────┘           │
  │           │                                                     │
  │           ▼                                                     │
  │  lastPortfolioId = 마지막 ID → LOOP 반복                         │
  └─────────────────────────────────────────────────────────────────┘
```

#### Lazy Warm-up (Monolith)

- 서비스 기동 시 전체 동기화 하지 않음
- 가격 이벤트 도착 시 해당 종목의 인덱스가 없으면 DB에서 lazy load
- `RedisIndexWarmupService.warmupIfAbsent(stockId)`
- 백그라운드 full warm-up도 지원 (`warmupAll()`)

---

## 7. 기존 코드와의 관계

| 기존 컴포넌트 | 변경 | 설명 |
|---|---|---|
| `ProfitCalculationService.recalculateAllProfits()` | **제거 대상** | Worker 파이프라인으로 대체 |
| `ProfitCalculationTxHelper` | **제거 대상** | 위와 함께 제거 |
| `AssetEventService.handleBatchPriceUpdatedEvent()` | **제거 대상** | Worker consumer가 담당 |
| `UserProfitRankingRepositoryImpl` | **수정** | DB + Redis ZSET 이중 저장 |
| `UserProfitRankingAggregationService` | **유지** | 주간/월간 계산 로직 유지 |
| `UserProfitSnapshotService` (Batch) | **유지** | 일일 스냅샷은 DB 유지 |
| `CurrentPriceService` (Monolith) | **수정** | Kafka produce 추가 |
| `AssetService` (Monolith) | **수정** | Redis 인덱스 갱신 이벤트 발행 |

---

## 8. 구현 파일 목록

### Monolith 신규 파일
- `modules/asset/infra/redis/StockHoldingIndexRedisRepository.java`
- `modules/asset/infra/redis/PortfolioAssetSnapshotRedisRepository.java`
- `modules/asset/infra/redis/PortfolioOwnerRedisRepository.java`
- `modules/asset/infra/redis/UserProfitSummaryRedisRepository.java`
- `modules/asset/infra/redis/UserProfitRankingRedisRepository.java`
- `modules/asset/application/event/AssetRegisteredEvent.java`
- `modules/asset/application/event/AssetUnregisteredEvent.java`
- `modules/asset/application/event/PortfolioCreatedEvent.java`
- `modules/asset/application/event/PortfolioDeletedEvent.java`
- `modules/asset/application/RedisIndexSyncService.java`
- `modules/asset/application/RedisIndexWarmupService.java`
- `modules/market/application/port/out/StockPriceEventProducer.java`
- `common/investment/dto/StockPriceUpdatedEvent.java`

### Monolith 수정 파일
- `modules/asset/application/AssetService.java` — 이벤트 발행 추가
- `modules/asset/application/event/AssetTransferredEvent.java` — target 스냅샷 필드 추가
- `modules/asset/infra/persistence/UserProfitRankingRepositoryImpl.java` — Redis ZSET 동기화
- `modules/market/application/CurrentPriceService.java` — Kafka produce 추가
- `modules/market/infra/messaging/MarketKafkaProducer.java` — StockPriceEventProducer 구현

### Batch 신규 파일
- `modules/asset/infra/redis/StockHoldingIndexRedisRepository.java`
- `modules/asset/infra/redis/PortfolioAssetSnapshotRedisRepository.java`
- `modules/asset/infra/redis/PortfolioOwnerRedisRepository.java`
- `modules/asset/infra/scheduler/RedisIndexReconciliationScheduler.java`

### Worker (`Finvibe_Profit_Worker`)
- `consumer/StockPriceUpdatedKafkaConsumer.java` — Kafka consumer
- `consumer/dto/StockPriceUpdatedEvent.java` — 가격 이벤트 DTO
- `config/WorkerConfig.java` — Bean 설정
- `domain/ProfitCalculation.java` — 순수 수익률 계산 로직 (AssetValuation + PortfolioValuation 재현)
- `service/PriceCoalescingBuffer.java` — 1초 윈도우 coalescing 버퍼
- `service/CurrentPriceCache.java` — 인메모리 현재가 캐시
- `service/RealtimeProfitUpdateService.java` — 핵심 파이프라인 (flush 스케줄러)
- `redis/StockHoldingIndexRedisRepository.java` — 종목→포트폴리오 인덱스 조회
- `redis/PortfolioAssetSnapshotRedisRepository.java` — 보유 자산 스냅샷 조회
- `redis/PortfolioOwnerRedisRepository.java` — 포트폴리오→유저 매핑 조회
- `redis/UserProfitRankingRedisRepository.java` — 랭킹 갱신
- `redis/UserProfitSummaryRedisRepository.java` — 수익률 요약 갱신

---

## 9. 검증 방법

1. **단위 테스트**: Redis Repository CRUD, 수익률 계산 로직
2. **통합 테스트**: 매수 → Redis 인덱스 갱신 확인, 가격 변동 → 수익률 반영 확인
3. **정합성 테스트**: Reconciliation 배치로 DB vs Redis diff 0건 확인
4. **Redis 장애 테스트**: Redis 다운 → fallback → Redis 복구 → 자동 보정
5. **부하 테스트**: 10만 유저 시뮬레이션, OOM 미발생 + 지연 < 30초 확인
6. **수익률 정확성**: 기존 배치 결과 vs 새 파이프라인 결과 교차 검증
