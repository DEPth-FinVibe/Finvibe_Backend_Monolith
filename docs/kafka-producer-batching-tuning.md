# Kafka Producer Batching & Compression 튜닝 — RecordAccumulator 내부 동작부터 실측까지

## 배경

Finvibe는 실시간 주가 데이터를 Kafka를 통해 수익 계산 파이프라인에 전달한다. 장 시작 시점에 수천 종목의 가격 이벤트가 동시에 발생하므로, Kafka Producer의 전송 효율이 전체 파이프라인 지연에 직결된다.

부하 테스트 환경(mock-market, 7,939종목, 100ms 간격)에서 baseline을 측정하고, Kafka Producer 내부 구조를 분석한 뒤 단계적으로 튜닝했다.

---

## 1. Kafka Producer 내부 파이프라인

Producer의 `send()`가 호출되면 메시지는 즉시 네트워크로 전송되지 않는다. 내부적으로 다음 파이프라인을 거친다:

```
Application Thread             Background Thread
     │                              │
     ▼                              │
  Serializer                        │
     │                              │
     ▼                              │
  Partitioner                       │
     │                              │
     ▼                              │
  RecordAccumulator ──────────► Sender Thread
     │  (partition별 Deque<Batch>)    │
     │                              ▼
     │                         NetworkClient
     │                              │
     │                              ▼
     │                         Broker (acks=all)
     │                              │
     ▼                              ▼
  BufferPool ◄─────────────── 전송 완료 후 ByteBuffer 반환
```

### RecordAccumulator

`send()` 호출 시 레코드는 `RecordAccumulator`의 내부 자료구조에 추가된다. 핵심은 **파티션별로 `Deque<ProducerBatch>`를 관리**한다는 점이다.

```
RecordAccumulator
├── Partition 0: [Batch_a (filling)] → [Batch_b (full, ready)]
├── Partition 1: [Batch_c (filling)]
├── Partition 2: [Batch_d (filling)] → [Batch_e (full, ready)]
...
```

각 `ProducerBatch`는 `batch.size` 바이트의 `ByteBuffer`를 `BufferPool`에서 할당받아 레코드를 축적한다. 배치가 가득 차거나 `linger.ms` 시간이 경과하면 Sender thread가 drain하여 broker로 전송한다.

### Sender Thread

Sender는 단일 스레드로 동작하며, 다음을 반복한다:

1. `RecordAccumulator.ready()` — 전송 가능한 파티션의 leader broker 목록 수집
2. `RecordAccumulator.drain()` — 해당 broker로 보낼 배치들을 꺼냄
3. `NetworkClient.send()` — ProduceRequest 생성, 소켓 전송
4. `NetworkClient.poll()` — 응답 수신, callback 실행

**`linger.ms`의 역할**: Sender thread가 `ready()`를 호출할 때, 배치가 가득 차지 않았더라도 `linger.ms`만큼 시간이 지났으면 "ready"로 판단한다. 즉 `linger.ms=0`이면 레코드가 들어오자마자 즉시 전송하고, `linger.ms > 0`이면 그 시간만큼 추가 레코드를 기다렸다가 한 번에 보낸다.

---

## 2. Baseline 측정 — 문제 발견

부하 테스트 환경에서 기본 설정(`linger.ms=0`, `batch.size=16KB`, 압축 없음)으로 측정한 결과:

| 지표 | 값 | 의미 |
|------|-----|------|
| batch_size_avg | **204 bytes** | 16KB 배치의 1.2%만 사용 |
| records_per_request_avg | **6.3** | 요청당 6건만 묶임 |
| record_send_rate | 267/s | 초당 전송 레코드 수 |
| request_rate | **42/s** | 초당 broker 요청 수 (높음) |
| io_wait_ratio | **0.87** | Sender thread가 87% 대기 |
| compression_rate | 1.0 | 무압축 |
| record_queue_time_avg | 11.6ms | RecordAccumulator 체류 시간 |
| record_queue_time_max | **1,318ms** | 최대 1.3초 체류 (스파이크) |

### 문제 진단

**`linger.ms=0`이 핵심 원인이다.**

`linger.ms=0`이면 RecordAccumulator에 레코드가 들어오는 즉시 Sender thread가 drain한다. 6개 파티션에 분산되므로 파티션당 배치에 1~2건만 담긴 채 전송된다.

결과:
- **배치가 거의 비어서 전송** → 네트워크 요청 횟수가 불필요하게 많음 (42 req/s)
- **Sender thread가 87% 대기** — 보낼 배치가 금방 소진되어 idle 상태
- **요청당 6건** — ProduceRequest의 오버헤드(헤더, CRC 등) 대비 payload가 작아 비효율적

---

## 3. 1차 튜닝 — linger.ms + batch.size

### 설정 변경

```java
configProps.put(ProducerConfig.LINGER_MS_CONFIG, 20);
configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768); // 32KB
```

**`linger.ms=20`의 의미**: Sender thread가 배치를 drain하기 전 최대 20ms를 대기한다. 이 20ms 동안 같은 파티션으로 향하는 레코드가 추가로 들어오면 하나의 배치에 묶인다.

**`batch.size=32KB`**: 기본 16KB에서 확장. linger.ms 동안 축적되는 레코드가 기존 배치 크기를 초과하지 않도록 여유를 확보.

### 1차 결과

| 지표 | Baseline | 1차 튜닝 | 변화 |
|------|----------|----------|------|
| batch_size_avg | 204 B | **467 B** | +129% |
| records_per_request | 6.3 | **25.9** | +311% |
| record_send_rate | 267/s | **423/s** | +58% |
| request_rate | 42/s | **16.6/s** | -60% |
| record_queue_time_avg | 11.6ms | 40.3ms | linger 반영 |
| record_queue_time_max | 1,318ms | **113ms** | -91% |

**queue_time_max 스파이크 91% 감소**가 주목할 만하다. baseline에서 1.3초 스파이크가 발생한 이유는 `linger.ms=0`에서도 Sender thread의 poll 주기와 OS 스케줄링에 의해 불규칙한 지연이 생기기 때문이다. `linger.ms=20`으로 설정하면 Sender의 drain 주기가 안정되어 스파이크가 사라진다.

---

## 4. 2차 튜닝 — linger.ms 증가 + LZ4 압축

### 설정 변경

```java
configProps.put(ProducerConfig.LINGER_MS_CONFIG, 100);
configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
```

**`linger.ms=100`**: 주가 이벤트는 100ms 간격으로 발생하므로, linger 100ms면 한 틱 사이클의 레코드를 모두 하나의 배치에 담을 수 있다.

**LZ4 압축**: JSON 직렬화된 주가 이벤트(`{"stockId":17,"price":50246,"updatedAt":"..."}`)는 필드명이 반복되므로 압축 효과가 크다. LZ4는 압축률은 zstd보다 낮지만 CPU 오버헤드가 거의 없어 latency-sensitive한 실시간 파이프라인에 적합하다.

### 압축 알고리즘 트레이드오프

| 알고리즘 | 압축률 | 압축 속도 | 적합한 케이스 |
|---------|--------|----------|-------------|
| none | - | - | 레코드 크기가 작고 CPU가 제약인 경우 |
| snappy | 중간 | 빠름 | 범용. Hadoop 생태계에서 표준 |
| **lz4** | 중간 | **매우 빠름** | **latency-sensitive한 실시간 처리** |
| zstd | 높음 | 느림 | 대용량 로그, 배치 처리 |

주가 이벤트 파이프라인은 end-to-end latency가 중요하므로 LZ4를 선택했다.

### 최종 결과

| 지표 | Baseline | 1차 (linger=20) | 2차 (linger=100, lz4) | 총 변화 |
|------|----------|-----------------|----------------------|---------|
| batch_size_avg | 204 B | 467 B | **506 B** | +148% |
| records_per_request | 6.3 | 25.9 | **64.3** | **+920%** |
| record_send_rate | 267/s | 423/s | **571/s** | +114% |
| request_rate | 42/s | 16.6/s | **8.9/s** | **-79%** |
| compression_rate | 1.0 | 1.0 | **0.49** | **51% 압축** |
| record_queue_time_avg | 11.6ms | 40.3ms | 99.5ms | linger 반영 |
| request_latency_avg | 8.6ms | 12.9ms | 15.6ms | 배치 증가로 소폭 상승 |

---

## 5. 트레이드오프 분석

### linger.ms와 end-to-end latency

`linger.ms=100`은 레코드가 RecordAccumulator에서 최대 100ms 대기한다는 의미다. 실시간 주가 서비스에서 100ms 추가 지연이 허용 가능한가?

- 주가 데이터의 최종 소비자는 **수익 계산 파이프라인**(profit-worker)이다
- profit-worker는 사용자의 보유 종목에 대한 미실현 수익을 갱신하는데, 100ms 수준의 지연은 사용자 체감 불가
- 반면 **실시간 차트 UI**는 Redis Pub/Sub으로 직접 전달하므로 Kafka latency의 영향을 받지 않음

따라서 linger.ms=100은 이 서비스에서 수용 가능한 트레이드오프다.

### batch_size_avg가 여전히 작은 이유

32KB 배치를 설정했지만 실제 평균은 506 bytes (1.5%)에 불과하다. 이는 다음 두 가지 원인이 결합된 결과다:

1. **6개 파티션 분산**: 100ms 동안 들어오는 레코드가 6개 파티션에 나뉘므로 파티션당 배치에 담기는 레코드 수가 제한됨
2. **변동분 필터링**: `CurrentPriceService`에서 가격이 변동된 경우에만 Kafka로 발행하므로 실제 발행량이 전체 틱 수보다 적음

Sticky Partitioner(KIP-480)가 `linger.ms > 0`일 때 같은 파티션에 몰아주는 최적화를 수행하지만, 현재는 stockId를 partition key로 사용하므로 Sticky Partitioner가 동작하지 않는다. key 기반 파티셔닝은 순서 보장을 위한 의도적 선택이므로, 배치 크기보다 correctness를 우선한다.

### Sender thread io_wait_ratio

io_wait_ratio가 0.89로 Sender thread가 89% 대기 중이다. 이는 "Sender가 병목"이 아니라 **"Sender에 여유가 충분하다"**는 의미다. Producer 앞단의 async executor가 Kafka send()에 공급하는 속도 자체가 현재 throughput의 상한이다.

---

## 6. 결론

| 관점 | 개선 |
|------|------|
| 네트워크 효율 | 요청 횟수 79% 감소 (42→8.9 req/s) |
| 배칭 효율 | 요청당 레코드 920% 증가 (6.3→64.3) |
| 대역폭 | LZ4 압축으로 전송량 51% 감소 |
| 처리량 | 초당 전송 114% 증가 (267→571 rec/s) |
| 안정성 | queue_time_max 스파이크 1,318ms→113ms |

핵심은 `linger.ms`, `batch.size`, `compression.type` 세 파라미터가 RecordAccumulator → Sender → NetworkClient 파이프라인에서 **서로 어떻게 엮이는지** 이해하고, 서비스 특성(주가 이벤트 주기, latency 허용 범위, 레코드 크기)에 맞게 조정하는 것이다.
