# Finvibe Backend Monolith

> **핀바이브(Finvibe)** 는 실시간 시세 기반 모의 투자와 경제 학습을 결합한 플랫폼입니다.  
> 이 README는 단순 기능 소개보다, 백엔드가 어떤 문제를 마주했고 어떤 기준으로 설계·검증했는지를 설명하기 위해 작성되었습니다.

## 목차

1. [프로젝트 한 줄 소개](#프로젝트-한-줄-소개)
2. [백엔드가 풀어야 했던 문제](#백엔드가-풀어야-했던-문제)
3. [아키텍처 개요](#아키텍처-개요)
4. [도메인 모듈 구조](#도메인-모듈-구조)
5. [실시간 시세와 수익률 계산 파이프라인](#실시간-시세와-수익률-계산-파이프라인)
6. [기술적 의사결정과 검증 사례](#기술적-의사결정과-검증-사례)
7. [관측성과 운영](#관측성과-운영)
8. [CI/CD와 배포](#cicd와-배포)
9. [로컬 실행](#로컬-실행)
10. [면접관에게 보여주고 싶은 점](#면접관에게-보여주고-싶은-점)

---

## 프로젝트 한 줄 소개

Finvibe는 사용자가 실제 주식 시세 흐름을 보며 모의 투자, 포트폴리오 관리, 랭킹, 토론, 뉴스, 학습 기능을 함께 사용할 수 있는 금융 학습 플랫폼입니다.

백엔드 관점에서 이 프로젝트의 핵심은 다음 세 가지입니다.

- **실시간성**: 증권사 WebSocket으로 들어오는 가격 변동을 빠르게 저장하고 사용자에게 전달한다.
- **정합성**: 가격 변동이 포트폴리오 평가금액, 수익률, 랭킹에 반영되는 경로를 안정적으로 설계한다.
- **운영 가능성**: Kafka, Redis, Batch, Worker, Observability를 통해 부하·장애·운영 이슈를 추적 가능하게 만든다.

---

## 백엔드가 풀어야 했던 문제

모의 투자 서비스는 단순 CRUD보다 상태 변화가 많습니다. 가격이 바뀌면 현재가 캐시, WebSocket 브로드캐스트, 포트폴리오 평가금액, 유저 수익률, 랭킹, 배지 조건이 함께 영향을 받습니다.

초기 구조에서는 수익률 갱신을 1시간 배치로 처리했고, 전체 유저를 대상으로 집계하는 방식이었습니다. 이 방식은 사용자가 늘어날수록 다음 문제가 커졌습니다.

| 문제 | 왜 문제가 되는가 |
|---|---|
| 전체 유저 GROUP BY 스캔 | 사용자가 늘수록 집계 비용이 선형적으로 증가 |
| 대량 컬렉션 동시 보관 | summaries, rankings, userIds 등 10만 단위 객체가 동시에 메모리에 존재 |
| DELETE ALL + INSERT ALL 랭킹 갱신 | JPA 엔티티가 대량 생성되어 OOM 위험 증가 |
| 단일 트랜잭션 fan-out | K(종목) × M(유저) × N(포트폴리오) 처리가 하나의 긴 흐름에 묶임 |

이 문제의 분석과 개선 방향은 [`docs/realtime-profit-pipeline.md`](docs/realtime-profit-pipeline.md)에 정리되어 있습니다. 문서에서는 10만 MAU 기준으로 여러 100K 컬렉션이 동시에 생길 때 약 300~500MB 메모리 영향이 발생할 수 있고, `replaceAllRankings()`에서 3개 랭킹 타입에 대해 최대 30만 JPA 엔티티를 만들 수 있음을 주요 병목으로 보았습니다.

---

## 아키텍처 개요

이 저장소는 Java 21, Spring Boot 4 기반의 백엔드 모놀리스입니다. 모놀리스 내부는 도메인별 모듈로 나누고, 각 모듈은 hexagonal architecture 형태로 구성했습니다.

```text
External
  KIS 증권사 WebSocket
        │
        ▼
Monolith
  market     : 시세 수신, 현재가 캐시, 가격 이벤트 발행
  asset      : 자산, 포트폴리오, 평가금액, 수익률
  trade      : 매수/매도 주문과 체결 흐름
  user       : 사용자와 인증
  wallet     : 예수금/지갑
  gamification : 배지, 챌린지, 랭킹 보상
  news/study/discussion : 학습과 커뮤니티 기능
        │
        ├── Redis Pub/Sub → WebSocket Listener → Client
        ├── Kafka → Profit Worker → Redis 수익률/랭킹 캐시
        └── MariaDB / Redis / Kafka
```

관련 런타임은 다음처럼 책임을 나눕니다.

| 컴포넌트 | 주 책임 |
|---|---|
| Monolith | API, 도메인 상태 변경, 시세 수신, Redis/Kafka 이벤트 발행 |
| WebSocket Listener | Redis Pub/Sub 가격 이벤트를 구독하고 클라이언트에 브로드캐스트 |
| Profit Worker | Kafka 가격 이벤트를 소비해 Redis 기반 수익률/랭킹 캐시 갱신 |
| Batch | 스냅샷, 랭킹, reconciliation 등 주기 작업 |
| Manifest Repo | ArgoCD 연동을 위한 Kubernetes manifest 관리 |

모놀리스가 모든 작업을 직접 처리하지 않고, 실시간 브로드캐스트·수익률 계산·배치 작업을 분리한 이유는 **관심사와 부하 특성이 다르기 때문**입니다. API 요청 처리, 실시간 이벤트 fan-out, Kafka consumer lag 기반 처리, 일괄 스냅샷 저장은 각각 병목 지점과 확장 방식이 다릅니다.

---

## 도메인 모듈 구조

실제 모듈은 `src/main/java/depth/finvibe/modules` 아래에 위치합니다.

| 모듈 | 역할 |
|---|---|
| `asset` | 포트폴리오, 보유 자산, 평가금액, 수익률 |
| `market` | 주식, 현재가, 시세 API, 가격 캐시 |
| `trade` | 주문, 매수/매도, 체결 이벤트 |
| `wallet` | 지갑, 잔고, 투자 가능 금액 |
| `user` | 사용자, 인증, OAuth/JWT 연동 |
| `gamification` | 배지, 챌린지, 랭킹 보상 |
| `news` | 뉴스 수집/조회 |
| `discussion` | 토론/댓글/커뮤니티 |
| `study` | 경제 학습 콘텐츠 |
| `dev` | 개발/운영 보조 기능 |

각 모듈은 다음 구조를 기준으로 나뉩니다.

```text
modules/<domain>/
  domain/              # Entity, Enum, ErrorCode 등 순수 도메인
  application/         # UseCase 구현과 서비스 흐름
    port/in/           # 외부에서 호출하는 입력 포트
    port/out/          # 저장소/외부 시스템으로 나가는 출력 포트
  infra/               # JPA, Redis, Kafka, 외부 API Adapter
  presentation or api/ # Controller, Request/Response DTO
```

이 구조의 목적은 “모놀리스이지만 모듈 경계를 흐리지 않는 것”입니다. 모듈 간 직접 의존을 줄이고 port 계약을 통해 연결하면, 추후 Worker/Batch 분리처럼 런타임이 나뉘어도 도메인 책임을 추적하기 쉽습니다.

---

## 실시간 시세와 수익률 계산 파이프라인

Finvibe의 실시간 흐름은 두 갈래로 나뉩니다.

1. 사용자가 보는 **실시간 시세 표시**
2. 포트폴리오와 랭킹에 반영되는 **수익률 계산**

```text
KIS WebSocket
  → Monolith CurrentPriceService
    ├─ Redis 현재가 저장
    ├─ Redis Pub/Sub 발행 → WebSocket Listener → Client
    └─ Kafka 발행 → Profit Worker → Redis 수익률/랭킹 캐시
```

처음에는 Worker가 Redis Pub/Sub을 직접 구독하는 방안도 검토했습니다. 구현은 단순하지만, 이벤트 유실과 배압 처리가 약하고 WebSocket 방송 채널과 수익률 계산 채널이 강하게 결합됩니다.

결국 가격 이벤트를 Kafka로 별도 발행하는 방식을 선택했습니다.

| 비교 항목 | Redis Pub/Sub 직접 구독 | Kafka 별도 이벤트 |
|---|---|---|
| 구현 난이도 | 낮음 | 중간 |
| 이벤트 유실 | Worker 다운 시 유실 가능 | Kafka 로그로 replay 가능 |
| 배압 처리 | 약함 | consumer lag으로 관측/흡수 가능 |
| 관심사 분리 | 시세 방송 채널과 계산 채널 공유 | 목적별 토픽 분리 |
| 확장성 | subscriber 추가 중심 | partition 기반 scale-out |

이 결정의 핵심은 “실시간 시세 UI와 수익률 계산은 모두 가격 이벤트를 쓰지만, 실패 모델과 운영 지표가 다르다”는 점이었습니다. 자세한 설계는 [`docs/realtime-profit-pipeline.md`](docs/realtime-profit-pipeline.md)에 정리되어 있습니다.

---

## 기술적 의사결정과 검증 사례

### 1. Kafka Producer batching과 LZ4 압축 튜닝

장 시작 시점에는 수천 종목의 가격 이벤트가 짧은 시간에 몰릴 수 있습니다. 기본 Producer 설정은 `linger.ms=0`, `batch.size=16KB`, 무압축이었고, 실측 결과 요청당 평균 6.3건만 묶여 broker 요청 수가 높았습니다.

부하 테스트 환경은 mock-market으로 7,939종목 가격 이벤트를 100ms 간격으로 발행하는 조건이었습니다. RecordAccumulator 동작을 분석한 뒤 `linger.ms=100`, `batch.size=32KB`, `compression.type=lz4`를 적용했습니다.

| 지표 | Baseline | 튜닝 후 | 변화 |
|---|---:|---:|---:|
| records/request | 6.3 | 64.3 | +920% |
| request_rate | 42/s | 8.9/s | -79% |
| record_send_rate | 267/s | 571/s | +114% |
| compression_rate | 1.0 | 0.49 | 51% 압축 |
| queue_time_max | 1,318ms | 113ms | 스파이크 감소 |

여기서 중요한 트레이드오프는 latency였습니다. `linger.ms=100`은 Kafka 전송 전에 최대 100ms를 기다릴 수 있다는 뜻입니다. 하지만 Kafka 경로는 수익률 계산용이고, 실시간 차트 UI는 Redis Pub/Sub 경로로 별도 전달됩니다. 따라서 UI 체감 실시간성을 해치지 않으면서 Kafka 네트워크 효율을 개선할 수 있었습니다.

근거 문서: [`docs/kafka-producer-batching-tuning.md`](docs/kafka-producer-batching-tuning.md)

### 2. Kafka leader failover 무손실 검증

가격 이벤트는 수익률 계산의 입력이기 때문에, broker 장애 시 메시지가 조용히 유실되는 구조는 피해야 했습니다. Kafka 토픽과 Producer는 다음 조건으로 구성했습니다.

| 항목 | 설정 |
|---|---|
| topic | `market.stock-price-updated.v1` |
| partition count | 6 |
| replication factor | 3 |
| min.insync.replicas | 2 |
| producer acks | all |
| idempotence | true |
| retries | `Integer.MAX_VALUE` |
| delivery timeout | 120000ms |

실측에서는 3 broker KRaft 클러스터에서 leader broker를 강제로 삭제했고, `NOT_LEADER_OR_FOLLOWER` 이후 metadata refresh와 retry를 거쳐 복구되는 흐름을 확인했습니다. failover 과정에서 retry 35건이 발생했지만, idempotent producer 설정으로 중복 저장을 방지하고 단일 broker 장애에서 메시지 유실 없이 복구되는 조건을 검증했습니다.

이 설계는 “절대 장애가 없다”는 보장이 아니라, **RF=3, minISR=2, acks=all 조건에서 단일 broker 장애를 유실 없이 흡수하도록 설계하고 검증했다**는 의미입니다.

근거 문서: [`docs/kafka-leader-failover-verification.md`](docs/kafka-leader-failover-verification.md)

### 3. 트랜잭션 안의 외부 I/O로 인한 커넥션 점유 문제 해결

운영 로그에서 HikariCP connection leak 경고가 반복되었습니다. 원인은 실제 커넥션 반환 누락이 아니라, 하나의 `@Transactional` 흐름 안에서 외부 HTTP API 호출과 대량 포트폴리오 계산이 함께 수행되어 DB 커넥션 점유 시간이 길어진 것이었습니다.

개선 전 흐름은 다음과 같았습니다.

```text
트랜잭션 시작 → DB 조회 → 외부 HTTP API 호출 → 평가금액 계산 → 전체 조회/랭킹 집계 → 트랜잭션 종료
```

개선 후에는 트랜잭션 경계를 작업 단위로 분리했습니다.

```text
짧은 read transaction  → 포트폴리오 조회
트랜잭션 없음          → 외부 가격 API 호출
짧은 write transaction → valuation 반영
짧은 read transaction  → 랭킹 집계용 조회
```

또한 같은 Bean 내부 호출로 인해 Spring AOP 트랜잭션 프록시가 우회되는 문제를 피하기 위해 `ProfitCalculationTxHelper`를 별도 Bean으로 분리했습니다. 이 사례는 “트랜잭션은 DB 작업을 보호하는 경계이지, 전체 비즈니스 오케스트레이션을 감싸는 만능 블록이 아니다”라는 교훈을 남겼습니다.

근거 문서: [`docs/devlog/connection-leak-fix.md`](docs/devlog/connection-leak-fix.md)

### 4. Redis/WebSocket 병목을 가정한 k6 테스트 설계

실시간 시세는 Redis Pub/Sub과 WebSocket fan-out을 거치므로, 단순 HTTP 부하 테스트만으로는 병목을 보기 어렵습니다. 그래서 `k6/hotkey` 테스트는 Redis latency, subscribe 초기 진입, current-price cache hotkey, mixed spike, 10k급 WebSocket 연결 유지 등 여러 트랙으로 나누어 설계했습니다.

특히 Stage 1은 단일 hotkey보다 Redis 전체 read pressure를 먼저 확인하는 방향이었습니다. 병목을 빨리 재현하기보다, 어떤 계층이 먼저 흔들리는지 분리해 보기 위한 결정입니다.

근거 문서: [`k6/hotkey/README.md`](k6/hotkey/README.md)

---

## 관측성과 운영

성능 개선과 장애 대응은 관측 지표가 있어야 반복 가능합니다. Finvibe는 Spring Actuator, Micrometer, Prometheus, Grafana, Loki, Grafana Alloy를 기반으로 메트릭과 로그를 수집하도록 구성했습니다.

```text
Metrics: Spring Boot → Micrometer → /actuator/prometheus → Prometheus → Grafana
Logs   : Spring Boot JSON log → Grafana Alloy → Loki → Grafana
```

README에서 강조하고 싶은 점은 도구 목록 자체보다, 어떤 질문에 답하기 위해 관측을 붙였는가입니다.

| 관측 대상 | 보는 이유 |
|---|---|
| p95/p99 HTTP latency | 특정 API가 평균이 아니라 tail latency에서 느려지는지 확인 |
| 5xx error rate | 배포 또는 외부 연동 장애를 빠르게 감지 |
| current-price cache hit/miss | Redis 캐시가 의도대로 작동하는지 확인 |
| Kafka producer/consumer 지표 | 가격 이벤트 처리량과 lag를 추적 |
| Loki JSON 로그 | 예외 원인과 특정 이벤트 흐름을 검색 가능하게 유지 |

관측성 문서에는 hit rate, miss rate, error rate, p99 latency를 PromQL로 해석하는 방법까지 정리했습니다.

근거 문서: [`docs/observability.md`](docs/observability.md)

---

## CI/CD와 배포

모놀리스 CI는 다음 흐름으로 구성되어 있습니다.

```text
GitHub Actions
  → Gradle clean build
  → Docker image build/push
  → Manifest repository checkout
  → backend/deployment.yaml image tag update
  → Manifest repository commit/push
  → ArgoCD sync 대상 변경
```

이 방식은 애플리케이션 코드 저장소와 Kubernetes manifest 저장소를 분리합니다. 애플리케이션 CI는 새 이미지를 만들고 manifest의 이미지 태그만 갱신하며, 실제 클러스터 반영은 manifest repo를 바라보는 GitOps 흐름에 맡깁니다.

근거 파일:
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- Manifest repo: `Finvibe_Backend_Manifest`

---

## 로컬 실행

### 사전 인프라 실행

```bash
docker compose -f infra/docker-compose.yml up -d
```

### 빌드와 테스트

```bash
./gradlew clean compileJava test
```

### 로컬 실행

```bash
./gradlew bootRun
```

OAuth 프로파일을 함께 사용할 때:

```bash
SPRING_PROFILES_ACTIVE=local,oauth ./gradlew bootRun
```

### Docker smoke test

```bash
docker build -t finvibe-backend-monolith:local .
```

---

## 면접관에게 보여주고 싶은 점

Finvibe 백엔드는 “기능을 구현했다”보다 “운영 중 문제가 될 수 있는 지점을 먼저 정의하고 검증했다”는 점을 보여주고자 합니다.

이 프로젝트에서 드러나는 백엔드 고민은 다음과 같습니다.

1. **정합성과 확장성 사이의 균형**  
   모든 수익률을 한 번에 재계산하는 단순한 구조에서, 가격 이벤트 기반 비동기 파이프라인으로 전환하며 fan-out과 OOM 위험을 줄였습니다.

2. **실시간성과 처리 효율의 분리**  
   UI 실시간 시세는 Redis Pub/Sub으로 빠르게 전달하고, 수익률 계산은 Kafka로 분리해 배압과 replay 가능성을 확보했습니다.

3. **측정 기반 최적화**  
   Kafka producer 튜닝은 감이 아니라 RecordAccumulator 동작과 실측 지표를 기반으로 진행했습니다. 요청당 레코드 수, 요청률, 압축률, queue time을 비교하며 trade-off를 판단했습니다.

4. **장애를 가정한 설계**  
   Kafka leader broker를 실제로 내리고 failover 흐름을 검증했습니다. `acks=all`, `min.insync.replicas=2`, idempotent producer가 왜 필요한지 운영 시나리오로 확인했습니다.

5. **트랜잭션 경계에 대한 이해**  
   connection leak 경고를 단순 설정 문제가 아니라 트랜잭션 내 외부 I/O 문제로 보고, read/write transaction과 orchestration을 분리했습니다.

6. **관측 가능한 시스템 지향**  
   Prometheus, Grafana, Loki를 통해 latency, error rate, cache hit/miss, Kafka 지표를 추적할 수 있도록 설계했습니다.

Finvibe는 완성된 기능의 목록보다, 실시간 금융 도메인에서 발생할 수 있는 부하·정합성·장애·운영 문제를 어떻게 나누어 보고 검증했는지를 보여주는 프로젝트입니다.
