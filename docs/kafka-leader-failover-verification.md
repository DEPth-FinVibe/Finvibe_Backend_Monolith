# Leader Failover 무손실 증명 — acks=all, ISR, Metadata Refresh 내부 동작 분석

## 배경

Kafka 클러스터에서 broker 장애는 불가피하다. 배포, OOM, 하드웨어 장애 등으로 leader broker가 내려가면, 해당 파티션의 leader가 ISR 내 다른 broker로 전환(failover)되어야 한다. 이 과정에서 **producer가 전송 중이던 메시지가 유실되는가?**

Finvibe의 주가 이벤트 파이프라인은 `acks=all` + `min.insync.replicas=2` + `ReplicationFactor=3`으로 구성되어 있다. 이 글에서는 실제 운영 중인 3-broker KRaft 클러스터에서 leader를 강제로 kill하고, producer 내부의 복구 메커니즘을 프로토콜 수준에서 분석하여 **단 한 건의 메시지도 유실되지 않음**을 실측으로 증명한다.

---

## 1. Replication 프로토콜 — acks=all이 보장하는 것

### acks 설정별 보장 수준

```
Producer                     Leader Broker              Follower Brokers
   │                              │                          │
   │── ProduceRequest ──────────►│                          │
   │   (acks=all)                 │                          │
   │                              │── Replicate ───────────►│
   │                              │                          │
   │                              │◄── Ack ─────────────────│
   │                              │   (모든 ISR이 복제 완료)   │
   │◄── ProduceResponse ─────────│                          │
   │   (success)                  │                          │
```

| acks | 동작 | 유실 가능성 |
|------|------|-----------|
| `0` | 전송만 하고 응답 안 기다림 | 높음 — leader crash 시 유실 |
| `1` | leader가 로컬에 저장 후 응답 | 중간 — leader crash 시 ISR에 아직 복제 안 된 레코드 유실 |
| `all` | **모든 ISR이 복제 완료 후 응답** | 없음 — ISR 내 최소 1대가 살아있으면 복구 가능 |

`acks=all`에서 producer가 성공 응답을 받았다면, 해당 레코드는 **ISR에 속한 모든 broker에 이미 복제**되어 있다. Leader가 죽어도 ISR의 다른 broker가 leader로 선출되면 해당 레코드는 보존된다.

### min.insync.replicas의 역할

`min.insync.replicas=2`는 `acks=all`일 때 **최소 2개 broker가 ISR에 있어야 쓰기를 허용**한다는 의미다.

```
상황 1: ISR = [100, 101, 102] → 3개 ≥ 2 → 쓰기 허용
상황 2: ISR = [101, 102]      → 2개 ≥ 2 → 쓰기 허용 (1대 장애 허용)
상황 3: ISR = [101]           → 1개 < 2 → NotEnoughReplicasException (쓰기 거부)
```

ReplicationFactor=3 + min.insync.replicas=2 조합은 **최대 1대의 broker 장애를 허용**하면서 데이터 내구성을 보장한다. 2대가 동시에 내려가면 쓰기 자체가 거부되어 **"조용한 유실"이 발생하지 않는다** — 유실보다 가용성 중단을 선택하는 설계다.

---

## 2. Leader Failover 내부 메커니즘

### KRaft Controller의 Leader Election

Kafka 4.x의 KRaft 모드에서는 ZooKeeper 대신 Raft 기반 controller quorum이 leader election을 수행한다.

```
시간축 →

Broker 100 (Leader)        KRaft Controller Quorum        Broker 101 (Follower)
       │                           │                            │
       │── heartbeat ────────────►│                            │
       │                           │                            │
       ✕ (crash)                   │                            │
                                   │                            │
                   heartbeat timeout (broker.session.timeout.ms)
                                   │                            │
                                   │── BrokerChangeRecord ────►│
                                   │   (100 removed from ISR)   │
                                   │                            │
                                   │── PartitionChangeRecord ─►│
                                   │   (Partition 2: leader     │
                                   │    100 → 101)              │
                                   │                            │
                                   │◄── Fetch 계속 ──────────│
                                   │   (101이 새 leader로 동작)  │
```

Controller가 broker 100의 heartbeat 실종을 감지하면:
1. ISR에서 100을 제거하고 `BrokerChangeRecord`를 metadata log에 기록
2. ISR의 나머지 broker 중 하나를 새 leader로 선출
3. `PartitionChangeRecord`를 metadata log에 기록
4. 모든 broker에 metadata update를 전파

### Producer의 Metadata Refresh — 두 가지 감지 경로

Producer는 leader 변경을 즉시 알 수 없다. Kafka client는 다음 두 경로로 leader 변경을 감지한다:

**경로 1: NOT_LEADER_OR_FOLLOWER 응답**

Broker가 아직 살아있지만 leader가 아닌 경우, ProduceRequest에 대해 `NOT_LEADER_OR_FOLLOWER` 에러를 반환한다. Producer의 `Sender` thread는 이 에러를 받으면:
1. 해당 배치를 RecordAccumulator의 재전송 큐에 다시 넣고
2. `NetworkClient`에 metadata refresh를 요청하고
3. 새 metadata를 수신한 뒤 새 leader에게 배치를 재전송한다

```
Producer                              Old Leader (100)
   │                                        │
   │── ProduceRequest ────────────────────►│
   │   Partition 2, leader=100              │
   │                                        │
   │◄── NOT_LEADER_OR_FOLLOWER ───────────│
   │                                        │
   │── MetadataRequest ──────────────────►│ (다른 broker로)
   │◄── MetadataResponse ────────────────│
   │   Partition 2: leader=101              │
   │                                        │
   │── ProduceRequest (retry) ──────────►│ New Leader (101)
   │◄── Success ──────────────────────────│
```

**경로 2: Node Disconnected**

Broker pod가 종료되어 TCP 연결 자체가 끊어지는 경우, `NetworkClient`가 `Node disconnected`를 감지한다. 이 경우 해당 node로 전송 중이던 모든 in-flight 요청이 실패 처리되고, retry 시 자동으로 metadata refresh가 트리거된다.

```
Producer                              Broker 100
   │                                        │
   │── ProduceRequest ──────── ✕ (소켓 끊김)
   │                                        
   │   NetworkClient: "Node 100 disconnected"
   │                                        
   │── retry 시 MetadataRequest ─────────►│ (다른 broker로)
   │                                        
   │── ProduceRequest (retry) ──────────►│ New Leader (101)
```

핵심: `retries=Integer.MAX_VALUE`와 `delivery.timeout.ms=120000`이 설정되어 있으므로, **2분 내에 새 leader가 선출되면 메시지는 유실되지 않는다.**

---

## 3. 실측 환경

### 클러스터 구성

```
KRaft Cluster (k3s, 3 broker + 3 controller 분리 배포)
├── kafka-controller-0  (Raft voter)
├── kafka-controller-1  (Raft voter)
├── kafka-controller-2  (Raft voter)
├── kafka-broker-0      (node.id=100)
├── kafka-broker-1      (node.id=101)
└── kafka-broker-2      (node.id=102)
```

### 토픽 설정

```
Topic: market.stock-price-updated.v1
  PartitionCount: 6
  ReplicationFactor: 3
  min.insync.replicas: 2
  cleanup.policy: compact
```

### Producer 설정

```java
acks = all
enable.idempotence = true
retries = Integer.MAX_VALUE
max.in.flight.requests.per.connection = 5
delivery.timeout.ms = 120000
linger.ms = 100
batch.size = 32768
compression.type = lz4
```

### 부하 조건

부하 테스트 프로필(`loadtest`)로 전체 종목(7,939개)에 대해 100ms 간격으로 가격 이벤트를 발행. 테스트 시점의 `record_send_rate ≈ 195/s`.

---

## 4. Broker Kill 테스트 — 실측 결과

### 4.1 Failover 전 상태

| Partition | Leader | Replicas | ISR |
|-----------|--------|----------|-----|
| 0 | **101** | 101, 102, 100 | 100, 101, 102 |
| 1 | **102** | 102, 100, 101 | 100, 101, 102 |
| 2 | **100** | 100, 101, 102 | 100, 101, 102 |
| 3 | **101** | 101, 102, 100 | 100, 101, 102 |
| 4 | **102** | 102, 100, 101 | 100, 101, 102 |
| 5 | **100** | 100, 101, 102 | 100, 101, 102 |

**Partition 2, 5의 leader가 broker-0 (node 100)**이므로, broker-0을 kill하면 이 두 파티션에서 failover가 발생한다.

Producer 지표 (baseline):

| 지표 | 값 |
|------|-----|
| record_send_total | 22,845 |
| record_error_total | 0 |
| record_retry_total | 0 |
| request_total | 2,128 |

### 4.2 Broker Kill

```bash
kubectl delete pod kafka-broker-0 -n finvibe
# 2026-04-28T03:15:04Z
```

### 4.3 Producer 로그 — Failover 시퀀스

```
[03:15:00.267] WARN  [Sender]
  Got error produce response with correlation id 2289 on
  topic-partition market.stock-price-updated.v1-5,
  retrying (2147483646 attempts left). Error: NOT_LEADER_OR_FOLLOWER

[03:15:00.359] WARN  [Sender]
  Received invalid metadata error in produce request on partition
  market.stock-price-updated.v1-5 due to NotLeaderOrFollowerException.
  Going to request metadata update now

[03:15:00.372] WARN  [Sender]
  Got error produce response with correlation id 2289 on
  topic-partition market.stock-price-updated.v1-2,
  retrying (2147483646 attempts left). Error: NOT_LEADER_OR_FOLLOWER

[03:15:00.453] WARN  [Sender]
  Received invalid metadata error in produce request on partition
  market.stock-price-updated.v1-2 due to NotLeaderOrFollowerException.
  Going to request metadata update now

[03:15:02.153] INFO  [NetworkClient]
  Node 100 disconnected.
```

### 4.4 시퀀스 분석

| 시각 | 이벤트 | 내부 동작 |
|------|--------|----------|
| 03:15:00.267 | Partition 5 `NOT_LEADER_OR_FOLLOWER` | Sender가 retry 예약. correlation id 2289 |
| 03:15:00.359 | Partition 5 metadata refresh 요청 | `NetworkClient`가 다른 broker에게 `MetadataRequest` 전송 |
| 03:15:00.372 | Partition 2 `NOT_LEADER_OR_FOLLOWER` | 같은 correlation id 2289 — **하나의 ProduceRequest에 Partition 2, 5가 함께 담겨 있었음** |
| 03:15:00.453 | Partition 2 metadata refresh 요청 | 이미 요청 중이므로 중복 요청하지 않음 |
| 03:15:02.153 | Node 100 disconnected | broker pod가 종료되어 TCP 연결 끊어짐 |

**correlation id 2289에 주목.** Kafka producer의 Sender thread는 같은 broker가 leader인 파티션들의 배치를 **하나의 ProduceRequest에 묶어서 전송**한다. broker-0이 Partition 2, 5의 leader였으므로 두 파티션의 배치가 동일한 요청(correlation id 2289)에 포함되어 있었고, 두 파티션 모두 같은 에러로 거부되었다.

에러 발생(03:15:00.267)부터 metadata refresh 요청(03:15:00.359)까지 **약 92ms**. Producer는 이미 다른 broker를 통해 새 leader 정보를 받고 retry를 진행했다.

### 4.5 Failover 후 — Leader 변경

| Partition | Leader (전) | Leader (후) | ISR (후) |
|-----------|------------|------------|----------|
| 0 | 101 | 101 | 101, 102 |
| 1 | 102 | 102 | 101, 102 |
| **2** | **100** | **101** | 101, 102 |
| 3 | 101 | 101 | 101, 102 |
| 4 | 102 | 102 | 101, 102 |
| **5** | **100** | **101** | 101, 102 |

Partition 2, 5의 leader가 100 → 101로 전환. ISR에서 100이 제거되어 [101, 102]만 남았지만, **min.insync.replicas=2를 충족**하므로 쓰기가 계속 허용됨.

### 4.6 Failover 후 — Producer 지표

| 지표 | Before Kill | After Kill | 의미 |
|------|------------|------------|------|
| record_send_total | 22,845 | 57,032 (+34,187) | failover 중에도 전송 지속 |
| **record_error_total** | **0** | **0** | **메시지 유실 0건** |
| **record_retry_total** | **0** | **35** | 35건 retry 발생, **전부 성공** |
| record_send_rate | 195/s | 372/s | 정상 처리량 복구 |

`record_error_total = 0`이 핵심이다. Retry가 35건 발생했지만, 모두 새 leader에게 재전송되어 성공했다. **단 한 건의 메시지도 유실되지 않았다.**

### 4.7 Broker 복구 후 ISR 재합류

Broker-0 pod가 Kubernetes의 StatefulSet에 의해 자동 재시작된 후, 모든 파티션의 ISR에 재합류했다:

```
Partition 0: ISR = [101, 102, 100]  ← 100 재합류
Partition 1: ISR = [101, 102, 100]
Partition 2: ISR = [101, 102, 100]
Partition 3: ISR = [101, 102, 100]
Partition 4: ISR = [101, 102, 100]
Partition 5: ISR = [101, 102, 100]
```

단, **leader는 자동으로 원래 broker로 복귀하지 않는다**. `auto.leader.rebalance.enable=true`(기본값)가 설정되어 있으면 controller가 주기적으로(`leader.imbalance.check.interval.seconds`, 기본 300초) preferred leader election을 수행하여 원래의 균등 분배로 복귀한다.

---

## 5. 왜 메시지가 유실되지 않는가 — 설정 간 상호작용

메시지 무손실은 단일 설정이 아닌 **여러 설정의 조합**으로 달성된다:

```
                    ┌─────────────────────────────┐
                    │    acks=all                  │
                    │    "ISR 전체가 복제해야 ACK"   │
                    └──────────┬──────────────────┘
                               │
                    ┌──────────▼──────────────────┐
                    │    min.insync.replicas=2     │
                    │    "ISR 2개 미만이면 쓰기 거부" │
                    └──────────┬──────────────────┘
                               │
           ┌───────────────────┼───────────────────┐
           │                   │                   │
┌──────────▼────────┐ ┌───────▼────────┐ ┌───────▼──────────────┐
│ enable.idempotence │ │ retries=MAX    │ │ delivery.timeout.ms  │
│ =true              │ │                │ │ =120000              │
│ "중복 전송 방지"     │ │ "무한 재시도"   │ │ "2분 내 전달 보장"     │
└────────────────────┘ └────────────────┘ └──────────────────────┘
```

각 설정이 빠졌을 때 어떤 일이 발생하는지를 이해하면, 이 조합이 왜 필요한지 명확해진다:

| 설정 | 역할 | 빠지면 어떤 일이 발생하는가 |
|------|------|-------------------------|
| `acks=all` | ACK 전 ISR 전체에 복제 | leader에만 저장 → leader crash 시 복제 안 된 레코드 유실 |
| `min.insync.replicas=2` | ISR 부족 시 쓰기 거부 | ISR=1일 때도 쓰기 허용 → 그 1대가 죽으면 유실 |
| `enable.idempotence=true` | retry 시 중복 저장 방지 | retry 성공해도 같은 레코드가 2번 저장됨 |
| `retries=MAX_VALUE` | 일시적 에러에 무한 재시도 | NOT_LEADER_OR_FOLLOWER 한 번에 포기 → 유실 |
| `delivery.timeout.ms=120000` | 재시도 시간 상한 | 명시하지 않으면 기본 2분이지만, 의도를 코드에 표현 |
| `max.in.flight.requests.per.connection=5` | 순서 보장하며 병렬 전송 | >5이면 idempotent 순서 보장 불가, =1이면 성능 저하 |

### Idempotent Producer와 Retry의 관계

이번 failover 테스트에서 35건의 retry가 발생했다. `enable.idempotence=true`가 없었다면, 이 35건 중 일부가 broker에 이미 저장된 뒤 ACK만 못 받은 상태에서 재전송되어 **중복 저장**되었을 것이다.

Idempotent producer의 sequence number 메커니즘(2장에서 상세 분석)이 broker 측에서 중복을 감지하므로, retry가 발생해도 **정확히 한 번만 저장**된다.

---

## 6. max.in.flight.requests.per.connection — 왜 5인가

Kafka의 idempotent producer는 `max.in.flight.requests.per.connection ≤ 5`일 때만 **파티션 내 순서 보장**을 한다. 이는 broker의 `ProducerStateManager`가 PID당 최근 5개 배치의 sequence를 추적하기 때문이다(KIP-98).

```
max.in.flight = 5일 때:

Sender Thread ──► Broker
  Batch A (seq=0) ──────► 저장, lastSeq=0
  Batch B (seq=1) ──────► 저장, lastSeq=1
  Batch C (seq=2) ──────► 저장, lastSeq=2
  Batch D (seq=3) ──────► 저장, lastSeq=3
  Batch E (seq=4) ──────► 저장, lastSeq=4
                          (5개 in-flight 도달, 다음 전송 대기)
```

```
Failover 중 재시도:

  Batch C (seq=2) ──── NOT_LEADER_OR_FOLLOWER
                       ↓
  metadata refresh → 새 leader 발견
                       ↓
  Batch C (seq=2) ──── retry → 새 leader에게 전송
                       ↓
  새 leader: seq=2, lastSeq=1 (이미 복제된 상태) → 정상 저장
```

만약 `max.in.flight > 5`이면, 5개를 초과하는 in-flight 배치가 실패 후 재전송될 때 broker의 sequence 추적 범위를 벗어나 `OutOfOrderSequenceException`이 발생할 수 있다. Kafka 3.x 이상에서 idempotent producer의 기본값이 5인 이유다.

---

## 7. 우리 서비스에서의 판단 — 추가 보호 장치 없이 충분한 이유

이번 테스트로 확인한 것은 **`acks=all` 기반 설정 조합이 단일 broker 장애에서 메시지를 잃지 않는다**는 것이다. Finvibe 파이프라인에 추가적인 보호 장치(예: application-level retry, DLQ fallback)가 필요한가?

### 불필요하다고 판단한 근거

1. **Kafka 내부 retry가 충분하다**: `retries=MAX_VALUE` + `delivery.timeout.ms=120000`으로 2분간 무한 재시도한다. 실측에서 failover 복구까지 2초도 걸리지 않았다 (03:15:00 에러 → 03:15:02 Node disconnected → retry 성공).

2. **2대 동시 장애는 쓰기 거부로 방어된다**: `min.insync.replicas=2`이므로 ISR이 1대만 남으면 `NotEnoughReplicasException`이 발생한다. 이 경우 producer의 `send()` callback에 에러가 전달되므로, application에서 감지 가능하다. "조용한 유실"은 발생하지 않는다.

3. **Consumer 측 idempotent 처리**: profit-worker는 stockId 기준 upsert이므로, 만약 retry로 인한 중복이 idempotent producer를 빠져나가더라도(PID 경계, 2장 참조) consumer에서 흡수된다.

4. **Compacted topic**: `cleanup.policy=compact`이므로 같은 stockId의 오래된 레코드는 compaction에서 제거된다.

---

## 8. 관련 KIP

### KIP-98: Exactly Once Delivery and Transactional Messaging

- Idempotent producer(PID, sequence number)와 transactional producer의 기반을 정의
- `max.in.flight.requests.per.connection ≤ 5` 제약의 근거가 되는 KIP
- Broker의 `ProducerStateManager`가 PID당 최근 5개 배치의 sequence를 추적하는 구조 정의

### KIP-279: Fix log divergence between leader and follower after fast leader fail over

- Leader failover 시 follower의 log가 새 leader와 diverge할 수 있는 문제 수정
- Follower가 새 leader로부터 fetch 시 `EpochEndOffset`를 확인하여 divergent log를 truncate
- 이 KIP 이전에는 unclean leader election 없이도 log divergence가 발생할 수 있었음

### KIP-966: Eligible Leader Replicas

- Kafka 4.x에서 도입. ISR에서 빠졌지만 leader가 될 수 있는 replica를 `ELR`(Eligible Leader Replicas)로 별도 관리
- 기존에는 ISR에서 빠진 replica가 바로 leader 후보에서 탈락 → 가용성 문제
- ELR은 ISR보다 느슨한 기준으로 "충분히 따라잡은" replica를 유지하여, ISR이 비었을 때도 데이터 유실 없이 leader election 가능

---

## 9. 결론

| 검증 항목 | 결과 |
|----------|------|
| Failover 발생 | broker-0 kill → Partition 2, 5의 leader가 100→101로 전환 |
| 감지~metadata 갱신 | NOT_LEADER_OR_FOLLOWER 발생 후 **92ms** 만에 metadata refresh 요청 |
| 메시지 유실 | **record_error_total = 0** — 유실 없음 |
| Retry | 35건 발생, **전부 성공** |
| 처리량 복구 | failover 후 372/s로 정상 복구 |
| ISR 재합류 | broker-0 재시작 후 전 파티션 ISR 복귀 확인 |

`acks=all` + `min.insync.replicas=2` + `retries=MAX_VALUE` + `enable.idempotence=true` 조합은 단일 broker 장애 시 메시지 무손실을 보장한다. Producer 내부의 `Sender` thread가 `NOT_LEADER_OR_FOLLOWER` 에러를 받으면 자동으로 metadata를 갱신하고, 실패한 배치를 새 leader에게 재전송한다. 이 과정이 수십 밀리초 내에 완료되며, idempotent producer의 sequence number가 retry로 인한 중복까지 방지한다.

**Kafka의 무손실 전송은 마법이 아니라, 각 설정이 정확히 맞물려 동작하는 프로토콜 수준의 보장이다.** 어떤 설정이 빠져도 구멍이 생기며, 이 조합이 왜 필요한지를 이해해야 운영 중 장애에서 "정말 메시지가 안 빠졌는지"를 확신할 수 있다.
