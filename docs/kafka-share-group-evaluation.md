# Share Group 도입 검토 — Consumer Group과의 비교, 적용 가능성, 한계 분석

## 배경

Kafka 4.0(KIP-932)에서 **Share Group**이 도입되었다. 기존 Consumer Group은 파티션을 consumer에게 정적으로 할당하여 순서 보장과 처리를 연결했다. Share Group은 이 모델을 깨고, **파티션 단위가 아닌 레코드 단위로 consumer에게 동적 분배**한다.

Finvibe의 Kafka 클러스터는 이미 Kafka 4.1.1 + `group.version=1`로 운영 중이어서 Share Group을 즉시 사용할 수 있는 상태다. 이 글에서는 Share Group의 내부 프로토콜을 분석하고, Finvibe의 각 토픽에 대해 도입 가능성과 한계를 평가한다.

---

## 1. Consumer Group의 구조적 제약

### 파티션-Consumer 정적 바인딩

Consumer Group에서 하나의 파티션은 **정확히 하나의 consumer에게 할당**된다. 이것이 파티션 내 순서 보장의 근거이면서, 동시에 확장성의 제약이 된다.

```
Topic: stock-price-updated.v1 (6 partitions)

Consumer Group: profit-worker (3 instances)

  Partition 0 ──► Consumer A
  Partition 1 ──► Consumer A
  Partition 2 ──► Consumer B
  Partition 3 ──► Consumer B
  Partition 4 ──► Consumer C
  Partition 5 ──► Consumer C
```

이 구조의 제약:

1. **Consumer 수 ≤ Partition 수**: consumer를 7개로 늘려도 6개 파티션에 1:1 매핑이므로 1개는 idle
2. **Hot Partition 문제**: 특정 파티션에 트래픽이 몰리면 해당 consumer만 과부하, 다른 consumer는 idle
3. **Rebalance 비용**: consumer 추가/제거 시 전체 파티션 재할당(rebalance)이 발생하고, 그 동안 처리가 중단됨

### Finvibe에서의 실제 상황

`market.stock-price-updated.v1` 토픽은 stockId를 partition key로 사용한다. 시가총액 상위 종목은 거래량이 많아 가격 변동이 빈번하지만, 하위 종목은 장 중에도 거의 변동이 없다. 이로 인해 **특정 파티션에 이벤트가 집중**될 수 있다.

```
Partition 0: [삼성전자, SK하이닉스, ...]  → 이벤트 빈번
Partition 1: [중소형주 A, B, C, ...]     → 이벤트 희박
Partition 2: [LG에너지솔루션, 현대차, ...] → 이벤트 빈번
...
```

Consumer Group 모델에서 Partition 0을 담당하는 consumer는 과부하되고, Partition 1을 담당하는 consumer는 대부분 idle이다. 파티션을 늘려도 key 기반 파티셔닝이므로 특정 key의 트래픽은 분산되지 않는다.

---

## 2. Share Group 프로토콜

### 레코드 단위 동적 분배

Share Group에서는 파티션-consumer 바인딩이 없다. Broker의 **Share-Partition Leader**가 레코드를 consumer들에게 동적으로 분배한다.

```
Topic: stock-price-updated.v1 (6 partitions)

Share Group: profit-share-group (3 instances)

  Partition 0 ─┬─► Consumer A (records 0-4)
               ├─► Consumer B (records 5-9)
               └─► Consumer C (records 10-14)
  Partition 1 ─┬─► Consumer A (records 0-2)
               ├─► Consumer B (records 3-5)
               └─► Consumer C (records 6-8)
  ...
```

**모든 consumer가 모든 파티션에서 레코드를 가져갈 수 있다.** Consumer 수가 파티션 수를 초과해도 모두 활용된다.

### Share Group의 핵심 개념

| 개념 | Consumer Group | Share Group |
|------|---------------|-------------|
| 할당 단위 | 파티션 | 레코드 |
| 순서 보장 | 파티션 내 순서 보장 | **순서 보장 없음** |
| Consumer 확장 | 파티션 수까지 | **무제한** |
| 오프셋 관리 | consumer가 commit | **broker가 per-record ACK 관리** |
| Rebalance | 필요 (파티션 재할당) | **불필요** (레코드 단위 분배) |

### Per-Record Acknowledgement

Consumer Group에서는 오프셋을 commit하면 그 오프셋 이전의 모든 레코드가 처리된 것으로 간주한다. Share Group에서는 **레코드별로 ACK/NACK**를 보낸다.

```
Share Group Consumer                       Broker (Share-Partition Leader)
     │                                          │
     │── ShareFetch ──────────────────────────►│
     │                                          │
     │◄── Records [offset 5, 6, 7] ───────────│
     │   (state: ACQUIRED)                      │
     │                                          │
     │   offset 5 처리 성공                      │
     │── ShareAcknowledge(5, ACCEPT) ─────────►│  → state: ACKNOWLEDGED
     │                                          │
     │   offset 6 처리 실패                      │
     │── ShareAcknowledge(6, REJECT) ─────────►│  → state: AVAILABLE (다른 consumer에게 재분배)
     │                                          │
     │   offset 7 처리 성공                      │
     │── ShareAcknowledge(7, ACCEPT) ─────────►│  → state: ACKNOWLEDGED
```

레코드의 생명주기:

```
AVAILABLE ──(ShareFetch)──► ACQUIRED ──(ACK)──► ACKNOWLEDGED
                               │
                               │──(NACK/timeout)──► AVAILABLE (재분배)
                               │
                               │──(max retries)──► ARCHIVED (포기)
```

### Share-Partition State 관리

Broker는 각 Share-Partition에 대해 **in-memory state + `__share_group_state` 내부 토픽**으로 레코드 상태를 관리한다.

```
__share_group_state (내부 토픽)
├── Share-Partition: stock-price-updated.v1-0
│   ├── offset 5: ACKNOWLEDGED
│   ├── offset 6: AVAILABLE (retry 1/3)
│   └── offset 7: ACKNOWLEDGED
├── Share-Partition: stock-price-updated.v1-1
│   └── ...
```

이 상태 추적이 Share Group의 핵심 오버헤드다. Consumer Group은 파티션당 하나의 오프셋만 관리하면 되지만, Share Group은 **개별 레코드의 상태를 추적**해야 한다.

---

## 3. Finvibe 토픽별 적용 가능성 평가

### 토픽 목록과 특성

| 토픽 | Producer | Consumer(s) | Key | 순서 의존성 | 처리 특성 |
|------|----------|-------------|-----|-----------|----------|
| `market.stock-price-updated.v1` | Monolith | Profit Worker | stockId | 낮음 (최신값 upsert) | Coalescing buffer → 10초 flush |
| `trade.trade-executed.v1` | Trade | Asset, Wallet | tradeId | **높음** (잔고 변경) | 정확히 한 번 처리 필요 |
| `trade.trade-reserved.v1` | Trade | Market | tradeId | **높음** (예약 상태 변경) | 상태 머신 전이 |
| `trade.trade-cancelled.v1` | Trade | Market | tradeId | **높음** (예약 취소) | 상태 머신 전이 |
| `market.reservation-satisfied.v1` | Market | Trade | tradeId | **높음** (체결 처리) | 정확히 한 번 처리 필요 |
| `user.signup.v1` | User | Asset, Wallet | userId | 낮음 | 초기화 (idempotent) |
| `gamification.update-user-metric.v1` | 여러 모듈 | Gamification | userId | 낮음 (누적 카운터) | delta 합산 |

### 평가 기준

Share Group 도입이 유효한 조건:
1. **순서 비의존**: 레코드 간 순서가 바뀌어도 최종 결과가 동일
2. **처리 시간 편차 큼**: 레코드마다 처리 시간이 달라 정적 파티션 할당이 비효율적
3. **확장성 필요**: 파티션 수 이상으로 consumer를 늘려야 하는 경우

### 토픽별 판정

#### market.stock-price-updated.v1 — 적용 가능

```
현재 (Consumer Group):
  Partition 0 ──► profit-worker-0  (삼성전자 등 대형주 → 바쁨)
  Partition 1 ──► profit-worker-0  (중소형주 → 한가함)
  ...

Share Group 적용 시:
  모든 Partition ──► 모든 profit-worker가 동적으로 가져감
  → Hot Partition 문제 해소
```

**적용 가능한 이유:**
- Profit Worker의 `PriceCoalescingBuffer`는 stockId 기준 `ConcurrentHashMap.merge()`로 최신 가격만 유지한다. 순서가 바뀌어도 `updatedAt` 비교로 최신값이 보존됨
- 10초 윈도우로 flush하므로, 윈도우 내에서 순서는 무의미 — 마지막 가격만 의미 있음
- Consumer 수를 파티션 수(6) 이상으로 확장 가능

**주의점:**
- `cleanup.policy=compact` 토픽에서 Share Group을 사용하려면, compacted 이전의 중복 레코드가 여러 consumer에게 분배될 수 있음. 하지만 `PriceCoalescingBuffer`가 이미 중복을 흡수하므로 문제없음

#### trade.trade-executed.v1 — 적용 불가

```
위험 시나리오:

  Record A: 매수 체결 (userId=1, stockId=100, qty=10)  → Consumer X
  Record B: 매도 체결 (userId=1, stockId=100, qty=5)   → Consumer Y (동시 처리)

  Consumer X: 잔고 +10 → DB write
  Consumer Y: 잔고 -5  → DB write (race condition)
```

거래 체결 이벤트는 **같은 사용자의 같은 종목에 대해 순서가 보장**되어야 한다. Consumer Group + tradeId key는 같은 사용자의 거래가 같은 파티션에 들어가므로 순서가 보장되지만, Share Group은 이를 깨뜨린다.

#### gamification.update-user-metric.v1 — 적용 가능 (제한적)

메트릭 업데이트는 delta 합산이므로 이론적으로 순서 비의존이다. 하지만 현재 단일 consumer로 처리량이 충분하므로 Share Group의 이점이 크지 않다.

#### user.signup.v1 — 적용 가능 (이점 없음)

회원가입은 빈도가 낮고 idempotent하다. Share Group의 이점이 없다.

---

## 4. market.stock-price-updated.v1에 Share Group 적용 시 아키텍처

### 현재 아키텍처 (Consumer Group)

```
Monolith (Producer)
    │
    ▼
market.stock-price-updated.v1 (6 partitions, compacted)
    │
    ▼
Consumer Group: profit-worker
    │
    ├── profit-worker-0 (Partition 0, 1 할당)
    │       └── PriceCoalescingBuffer
    │              └── 10s flush → DB upsert
    │
    ├── profit-worker-1 (Partition 2, 3 할당)
    │       └── PriceCoalescingBuffer
    │              └── 10s flush → DB upsert
    │
    └── profit-worker-2 (Partition 4, 5 할당)
            └── PriceCoalescingBuffer
                   └── 10s flush → DB upsert
```

**제약**: consumer 최대 6개 (파티션 수). Hot partition 시 특정 worker 과부하.

### Share Group 적용 후

```
Monolith (Producer)
    │
    ▼
market.stock-price-updated.v1 (6 partitions, compacted)
    │
    ▼
Share Group: profit-share-group
    │
    ├── profit-worker-0 (모든 파티션에서 동적 fetch)
    │       └── PriceCoalescingBuffer
    │              └── 10s flush → DB upsert
    │
    ├── profit-worker-1 (모든 파티션에서 동적 fetch)
    │       └── PriceCoalescingBuffer
    │              └── 10s flush → DB upsert
    │
    ├── ...
    │
    └── profit-worker-N (N > 6도 가능)
            └── PriceCoalescingBuffer
                   └── 10s flush → DB upsert
```

**이점**:
- Consumer 수 제한 없음
- Hot partition 자동 분산
- Rebalance 없음 — consumer 추가/제거가 즉시 반영

### 코드 변경량

Share Group 전환은 consumer 측의 설정 변경만으로 가능하다:

```java
// Before (Consumer Group)
@KafkaListener(
    topics = "market.stock-price-updated.v1",
    groupId = "profit-worker"
)

// After (Share Group)
@KafkaListener(
    topics = "market.stock-price-updated.v1",
    groupId = "profit-share-group",
    properties = {
        "group.type=share"
    }
)
```

단, Spring Kafka 4.x에서 Share Group consumer의 `AckMode` 설정이 필요하다. Share Group은 per-record ACK가 기본이므로, `AckMode.RECORD`와 호환된다.

---

## 5. Share Group의 한계와 트레이드오프

### 순서 보장 없음

Share Group의 가장 큰 제약이다. 같은 파티션의 레코드가 서로 다른 consumer에게 분배되므로, **파티션 내 순서 보장이 깨진다**.

```
Consumer Group:
  Partition 0: [A, B, C, D] → Consumer X가 순서대로 처리

Share Group:
  Partition 0: [A, B, C, D]
    A → Consumer X
    B → Consumer Y  ← B가 A보다 먼저 처리될 수 있음
    C → Consumer X
    D → Consumer Z
```

이것이 `trade.trade-executed.v1`에 Share Group을 적용할 수 없는 이유다.

### Per-Record State 오버헤드

Consumer Group은 파티션당 하나의 committed offset만 관리한다. Share Group은 **ACQUIRED 상태인 모든 레코드의 상태를 개별 추적**한다.

```
Consumer Group 상태 저장:
  __consumer_offsets: { partition-0: offset 1000 }  → 고정 크기

Share Group 상태 저장:
  __share_group_state: {
    partition-0-offset-997: ACQUIRED (consumer A)
    partition-0-offset-998: ACKNOWLEDGED
    partition-0-offset-999: ACQUIRED (consumer B)
    partition-0-offset-1000: AVAILABLE
  }  → 레코드 수에 비례
```

높은 throughput의 토픽에서는 이 상태 관리가 broker의 메모리와 `__share_group_state` 토픽의 I/O를 증가시킨다.

### Delivery Count와 재처리

Share Group은 레코드별로 `delivery.count`를 추적한다. Consumer가 NACK하거나 ACK 없이 timeout(`share.record.lock.duration.ms`, 기본 30초)되면, 해당 레코드는 AVAILABLE로 돌아가 다른 consumer에게 재분배된다.

최대 재시도 횟수(`share.group.delivery.count.limit`, 기본 5)를 초과하면 레코드는 **ARCHIVED** 상태가 되어 더 이상 처리되지 않는다.

```
Record lifecycle:
  AVAILABLE → ACQUIRED → (timeout) → AVAILABLE → ACQUIRED → ... → ARCHIVED
                                                                      │
                                                        DLQ로 보내야 할 시점
```

Consumer Group에서는 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`로 DLQ 전환이 표준화되어 있지만, Share Group에서의 DLQ 처리는 아직 Spring Kafka에서 성숙하지 않았다(2026년 4월 기준).

### Early Access 상태

Kafka 4.0에서 Share Group은 **Early Access**로 도입되었다. 프로덕션 사용은 가능하지만, 향후 마이너 릴리스에서 프로토콜 변경이 있을 수 있다. Kafka 4.1.1(현재 운영 버전)에서는 안정성이 개선되었으나, Consumer Group 대비 운영 경험이 축적되지 않은 상태다.

---

## 6. Consumer Group vs Share Group — 판단 매트릭스

| 기준 | Consumer Group | Share Group | Finvibe 판단 |
|------|---------------|-------------|-------------|
| 순서 보장 | 파티션 내 보장 | 없음 | 거래 토픽 → CG, 가격 토픽 → SG 가능 |
| 확장 상한 | 파티션 수 | 없음 | 현재 파티션 6개로 충분하지만, 종목 수 증가 시 SG 유리 |
| Rebalance | 발생 (처리 중단) | 없음 | SG가 운영 안정성에서 유리 |
| 오프셋 관리 | 단순 (파티션당 1개) | 복잡 (레코드당) | CG가 broker 부하 면에서 유리 |
| DLQ/에러 처리 | 성숙 | Early Access | CG가 현시점에서 안전 |
| Hot Partition | 수동 대응 필요 | 자동 분산 | SG가 유리 |
| Spring Kafka 지원 | 완전 | 기본 지원 (4.x) | CG가 생태계 면에서 유리 |

---

## 7. 우리 서비스에서의 판단

### 현시점: Consumer Group 유지

현재 Finvibe의 주가 이벤트 파이프라인은 Consumer Group으로 운영한다. 이유:

1. **현재 throughput에서 파티션 6개가 충분하다**: Profit Worker가 `PriceCoalescingBuffer`로 10초 윈도우 coalescing을 수행하므로, 실제 DB 부하는 10초당 종목 수(최대 7,939건)다. Consumer 1대로도 처리 가능한 수준.

2. **거래 토픽은 Share Group 적용 불가**: `trade.trade-executed.v1`은 잔고 변경의 순서가 중요하므로 Consumer Group이 필수. 가격 토픽만 Share Group으로 전환하면 두 가지 consumer 모델이 공존하여 운영 복잡도가 증가한다.

3. **Share Group은 아직 Early Access다**: 프로덕션 안정성보다 기능 검증이 우선인 단계. 에러 처리, 모니터링 도구, Spring Kafka 통합이 더 성숙해질 때까지 기다리는 것이 합리적이다.

### 전환 조건 — 이 조건이 충족되면 도입 검토

1. **종목 수 10,000개 이상 + 실시간 가격 업데이트 주기 50ms 이하**: Profit Worker 단일 인스턴스의 처리 한계를 초과하여 consumer 수를 파티션 이상으로 확장해야 할 때
2. **Share Group이 GA(General Availability)로 승격**: Kafka 커뮤니티에서 프로덕션 안정성이 검증된 후
3. **Spring Kafka의 Share Group DLQ 지원**: `@KafkaListener`에서 Share Group의 NACK/ARCHIVED를 DLQ로 자동 전환하는 기능이 추가된 후

---

## 8. 관련 KIP

### KIP-932: Queues for Kafka

- Kafka 4.0에서 도입. Share Group의 핵심 KIP
- 새로운 group protocol: `ShareFetch`, `ShareAcknowledge` API 정의
- `__share_group_state` 내부 토픽으로 per-record 상태 관리
- `group.type=share`로 기존 Consumer Group과 구분
- 하나의 토픽에 Consumer Group과 Share Group이 동시에 구독 가능

### Consumer Group과의 공존

하나의 토픽에 Consumer Group과 Share Group이 동시에 구독할 수 있다. 예를 들어, `market.stock-price-updated.v1`에 기존 Consumer Group(profit-worker)과 새로운 Share Group(analytics-share-group)을 함께 연결하여, 기존 시스템을 건드리지 않고 새 consumer를 추가할 수 있다. 이 공존 가능성이 점진적 마이그레이션을 가능하게 한다.

### KIP-1015: Share Group Improvements

- Share Group의 운영성 개선. 모니터링 도구, admin API 추가
- `kafka-share-groups.sh`로 Share Group 상태 조회
- `__share_group_state` 토픽의 compaction 정책 최적화

---

## 9. 결론

| 평가 항목 | 결과 |
|----------|------|
| 클러스터 호환성 | Kafka 4.1.1, `group.version=1` — **즉시 사용 가능** |
| 적용 가능 토픽 | `market.stock-price-updated.v1`, `gamification.update-user-metric.v1` |
| 적용 불가 토픽 | `trade.trade-executed.v1`, `trade.trade-reserved.v1` (순서 의존) |
| 현시점 판단 | **Consumer Group 유지** — throughput 여유 충분, Early Access 리스크 |
| 전환 시점 | 종목 10,000+ 또는 업데이트 주기 50ms 이하 + Share Group GA 후 |

Share Group은 Kafka에 **큐 시맨틱**을 도입한 중요한 진화다. 파티션-consumer 정적 바인딩의 제약을 해소하고, consumer 확장을 파티션 수와 독립적으로 만든다. 하지만 **순서 보장 포기**라는 근본적 트레이드오프가 있으므로, 모든 토픽에 일괄 적용할 수 없다.

Finvibe의 경우, 가격 이벤트 토픽은 `PriceCoalescingBuffer`의 설계 덕분에 Share Group과 자연스럽게 호환되지만, 현재 throughput에서는 Consumer Group으로 충분하다. **"지금 당장 필요하지 않지만, 확장 시 첫 번째 선택지"**가 Share Group에 대한 현시점의 올바른 위치 설정이다.
