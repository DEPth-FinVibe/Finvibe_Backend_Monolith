# Idempotent Producer 중복 방지 메커니즘 검증 — PID, Epoch, Sequence Number 내부 동작 분석

## 배경

Finvibe의 주가 이벤트 파이프라인은 `enable.idempotence=true`로 설정되어 있다. Kafka의 idempotent producer는 네트워크 재전송 시 broker 측에서 중복을 감지하여 정확히 한 번만 저장하는 것을 보장한다.

하지만 "idempotent producer = exactly-once"가 아니다. 이 글에서는 idempotent producer의 내부 메커니즘을 프로토콜 수준에서 분석하고, 실제 운영 로그와 broker 저장소를 검증하여 **어디까지가 중복 방지이고 어디서부터 한계인지**를 명확히 한다.

---

## 1. Idempotent Producer 프로토콜

### PID(Producer ID)와 Epoch 할당

Producer가 최초로 `send()`를 호출하면, 내부의 `TransactionManager`가 broker에 `InitProducerIdRequest`를 전송한다. Broker의 Transaction Coordinator는 고유한 **PID(Producer ID)**와 **epoch**를 할당한다.

```
Producer                                    Broker (Transaction Coordinator)
   │                                              │
   │──── InitProducerIdRequest ──────────────────►│
   │                                              │
   │◄─── InitProducerIdResponse ──────────────────│
   │     (PID=6, epoch=0)                         │
   │                                              │
```

실제 운영 로그에서 확인한 할당:

```
[kafka-producer-network-thread | finvibe-backend-monolith-producer-1]
  ProducerId set to 6 with epoch 0
```

### Sequence Number에 의한 중복 감지

PID가 할당되면, producer는 **각 `<PID, TopicPartition>` 조합에 대해 sequence number를 0부터 자동 증가**시킨다. Broker는 in-memory map으로 `<PID, TopicPartition> → lastSequence`를 추적한다.

```
Producer                              Broker (Partition Leader)
   │                                        │
   │── ProduceRequest ────────────────────►│
   │   PID=6, Partition=0, Seq=0            │  lastSeq[-1] → 0 OK, 저장
   │                                        │
   │── ProduceRequest ────────────────────►│
   │   PID=6, Partition=0, Seq=1            │  lastSeq[0] → 1 OK, 저장
   │                                        │
   │── ProduceRequest (재전송) ──────────►│
   │   PID=6, Partition=0, Seq=1            │  lastSeq[1] → 1 중복!
   │                                        │  DuplicateSequenceException
   │◄── 성공 응답 (중복이지만 에러 아님) ──│
   │                                        │
```

핵심: broker는 `seq == lastSeq + 1`이면 정상 저장, `seq <= lastSeq`이면 중복으로 판단하여 **저장하지 않되 성공 응답**을 반환한다. `seq > lastSeq + 1`이면 `OutOfOrderSequenceException`으로 거부한다.

---

## 2. 실측 검증 — kafka-dump-log로 PID/Sequence 확인

`kafka-dump-log.sh`로 broker의 실제 로그 세그먼트를 덤프하여 PID와 sequence number를 직접 확인했다.

### 로그 레코드 구조

```
baseOffset: 0  lastOffset: 2  count: 3
  baseSequence: 0  lastSequence: 2
  producerId: 2  producerEpoch: 0
  partitionLeaderEpoch: 0
  isTransactional: false
  compresscodec: none
  |  offset: 0  sequence: 0  key: 17
  |    payload: {"stockId":17,"price":50246,"updatedAt":"2026-04-27T05:19:17"}
  |  offset: 1  sequence: 1  key: 5
  |    payload: {"stockId":5,"price":50205,"updatedAt":"2026-04-27T05:19:16"}
  |  offset: 2  sequence: 2  key: 15
  |    payload: {"stockId":15,"price":49554,"updatedAt":"2026-04-27T05:19:17"}
```

확인할 수 있는 사실:
- **producerId: 2** — 이 배치를 전송한 producer의 PID
- **producerEpoch: 0** — 해당 PID의 epoch
- **baseSequence: 0, lastSequence: 2** — 이 배치에 담긴 레코드의 sequence 범위
- **sequence: 0, 1, 2** — 각 레코드의 sequence number가 순차 증가

### Sequence 연속성 검증

연속된 배치에서 sequence가 끊기지 않고 증가하는 것을 확인:

```
baseOffset: 0   → baseSequence: 0,  lastSequence: 2   (PID=2)
baseOffset: 3   → baseSequence: 3,  lastSequence: 3   (PID=2)
baseOffset: 4   → baseSequence: 4,  lastSequence: 4   (PID=2)
baseOffset: 5   → baseSequence: 5,  lastSequence: 5   (PID=2)
baseOffset: 6   → baseSequence: 6,  lastSequence: 7   (PID=2)
baseOffset: 8   → baseSequence: 8,  lastSequence: 8   (PID=2)
...
```

Sequence가 0→1→2→3→...으로 빈 구간 없이 증가한다. 이는 이 PID의 생존 기간 동안 **중복도, 유실도 없었음**을 의미한다.

---

## 3. PID 재할당 경계 — Idempotent의 한계

### Producer 재시작 시 PID 변경

운영 중 배포, OOM, Pod 재시작 등으로 producer가 종료되면, 재시작 시 **새로운 PID가 할당**된다. `kafka-dump-log`에서 PID 전환 이력을 추출했다:

```
PID 2 → 3  at offset 29,268  (sequence 0으로 리셋)
PID 3 → 4  at offset 50,559  (sequence 0으로 리셋)
PID 4 → 5  at offset 104,913 (sequence 0으로 리셋)
PID 5 → 6  at offset 156,371 (sequence 0으로 리셋)
```

| PID | epoch | offset 범위 | 배치 수 | 원인 |
|-----|-------|------------|--------|------|
| 2 | 0 | 0 ~ 29,267 | 17,950 | 최초 배포 |
| 3 | 0 | 29,268 ~ 50,558 | 6,391 | producer 재시작 |
| 4 | 0 | 50,559 ~ 104,912 | 11,859 | producer 재시작 |
| 5 | 0 | 104,913 ~ 156,370 | 11,560 | producer 재시작 |
| 6 | 0 | 156,371 ~ | 7,222+ | 현재 활성 |

모든 PID 전환에서 **sequence가 0으로 리셋**되는 것을 확인했다.

### 중복 발생 가능 시나리오

PID 재할당 경계에서 다음과 같은 중복이 이론적으로 가능하다:

```
시간축 →

PID 2 (기존 producer)
  ... seq 17949 → send() → 네트워크 전송 → broker 저장 완료
                                              ↓
                             ACK 수신 전 producer crash
                                              ↓
PID 3 (재시작된 producer)                      
  seq 0 → send() → 동일 데이터 전송 → broker는 PID 3으로 인식 → 저장
                                              ↓
                                     같은 이벤트가 offset 29267, 29268에 중복 저장
```

**Broker는 PID가 다르면 별개의 producer로 취급**한다. PID 2의 sequence 17949와 PID 3의 sequence 0은 독립적인 시퀀스이므로, 같은 데이터가 두 번 저장되어도 broker는 이를 중복으로 감지하지 못한다.

이것이 **"idempotent producer는 at-most-once가 아닌 at-least-once"**인 이유다.

---

## 4. Exactly-Once와의 차이 — Transactional Producer

이 한계를 해결하는 것이 **Transactional Producer** (KIP-98)다.

| 특성 | Idempotent Producer | Transactional Producer |
|------|-------------------|----------------------|
| 설정 | `enable.idempotence=true` | `transactional.id` 설정 |
| PID 관리 | 매 시작마다 새 PID | **같은 `transactional.id`면 같은 PID 유지, epoch만 증가** |
| 재시작 시 | sequence 리셋, 중복 가능 | epoch fencing으로 이전 PID의 미완료 배치 무효화 |
| 보장 수준 | at-least-once | exactly-once (consumer `read_committed` 필요) |
| 성능 오버헤드 | 거의 없음 | 2PC(PREPARE → COMMIT) 오버헤드 |

Transactional Producer에서는 `transactional.id`가 같으면 재시작 시에도 **같은 PID를 유지하고 epoch만 1 증가**시킨다 (KIP-360). Broker는 이전 epoch의 미완료 배치를 fencing(무효화)하여 중복을 방지한다.

### Epoch Fencing 동작

```
Producer (restart)                    Broker (Transaction Coordinator)
   │                                        │
   │── InitProducerIdRequest ──────────────►│
   │   transactional.id = "finvibe-prod"    │
   │                                        │
   │◄── InitProducerIdResponse ────────────│
   │   PID=6, epoch=1 (이전 epoch=0)        │
   │                                        │
   │                                        │── epoch=0의 미완료 배치 → ABORT
   │                                        │
```

epoch가 0→1로 증가하면, broker는 epoch=0으로 전송된 모든 미완료(uncommitted) 배치를 자동으로 abort 처리한다. 이로써 PID 재할당 경계의 중복 문제가 해결된다.

---

## 5. 우리 서비스에서의 판단 — at-least-once로 충분한 이유

Finvibe의 주가 이벤트 파이프라인에서는 **Transactional Producer 없이 idempotent producer(at-least-once)로 충분**하다고 판단했다. 그 이유:

### 1. 토픽의 cleanup.policy=compact

```
Topic: market.stock-price-updated.v1
  cleanup.policy=compact
  key=stockId
```

Compacted topic은 **같은 key에 대해 최신 값만 유지**한다. PID 경계에서 중복이 발생하더라도, 같은 stockId의 이전 레코드는 compaction 시 제거된다.

### 2. Consumer의 idempotent 처리

profit-worker는 stockId 기준으로 수익을 재계산(upsert)한다. 같은 가격 이벤트가 두 번 도착해도 결과가 동일하므로 **consumer 로직 자체가 idempotent**하다.

### 3. 성능 트레이드오프

Transactional Producer는 2PC 오버헤드로 인해 throughput이 3~20% 감소한다. 초당 수백 건의 주가 이벤트를 처리하는 파이프라인에서, 이미 idempotent한 consumer가 있는 상황에서 추가적인 성능 비용을 지불할 이유가 없다.

---

## 6. 관련 KIP

### KIP-98: Exactly Once Delivery and Transactional Messaging

- Kafka 0.11에서 도입된 핵심 KIP
- Idempotent producer(PID + sequence number)와 Transactional producer(`transactional.id` + 2PC)를 모두 포함
- `__transaction_state` 내부 토픽에 트랜잭션 상태(PREPARE, COMMIT, ABORT)를 기록하는 구조 정의

### KIP-360: Improve Handling of Unknown Producer

- Producer 재시작 후 epoch fencing에서 발생할 수 있는 `UNKNOWN_PRODUCER_ID` 에러 처리 개선
- Broker가 PID의 마지막 sequence를 로그 세그먼트에서 복구하는 메커니즘 보강
- `ProducerStateManager`가 로그 세그먼트의 snapshot 파일(`.snapshot`)을 통해 PID 상태를 영속화

---

## 7. 결론

| 검증 항목 | 결과 |
|----------|------|
| PID/epoch 할당 | `InitProducerIdRequest` → broker가 (PID=6, epoch=0) 할당 확인 |
| Sequence 연속성 | 모든 PID 내에서 0, 1, 2, ... 빈 구간 없이 증가 |
| PID 재시작 시 리셋 | 5회 PID 전환, 매번 sequence 0으로 리셋 확인 |
| 중복 방지 범위 | 같은 PID 내에서만 유효. PID 변경 시 경계에서 중복 가능 |
| Exactly-once 달성 조건 | `transactional.id` 설정 + consumer `read_committed` 필요 |
| 서비스 판단 | compacted topic + idempotent consumer로 at-least-once 충분 |

Idempotent producer는 **"같은 producer 인스턴스의 생존 기간 내에서 네트워크 재전송에 의한 중복을 방지"**하는 것이지, 모든 상황에서의 exactly-once를 보장하는 것이 아니다. 이 한계를 정확히 이해하고, 서비스 특성에 맞는 보장 수준을 선택하는 것이 중요하다.
