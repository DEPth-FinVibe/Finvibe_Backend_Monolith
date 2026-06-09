# Finvibe Backend Monolith

실시간 시세 기반 모의 투자 플랫폼 Finvibe의 API 서버입니다. Java 21, Spring Boot 4 기반 모듈형 모놀리스로, KIS 증권사 WebSocket 시세를 수신하여 Redis Pub/Sub(UI)과 Kafka(수익률 계산)로 이벤트를 발행합니다.

> WebSocket Listener, Profit Worker의 개선 사항은 각 저장소 README를 참고하세요.

## 아키텍처

```text
KIS WebSocket → Monolith ─┬─ Redis Pub/Sub → WebSocket Listener → Client
                           ├─ Kafka → Profit Worker → Redis 수익률/랭킹
                           └─ MariaDB (도메인 상태)
```

모놀리스 내부는 도메인별 모듈(`market`, `asset`, `trade`, `user`, `wallet`, `gamification`, `news`, `discussion`, `study`)로 나누고 hexagonal architecture(port/in, port/out) 형태로 구성했습니다.

---

## 모놀리스에서 수행한 개선

### 1. Kafka Producer 튜닝 — RecordAccumulator 분석 기반

- **문제**: 장 시작 시 수천 종목 이벤트가 몰리는데, 기본 설정(`linger.ms=0`, `batch.size=16KB`)에서 요청당 6.3건만 묶여 broker 요청 과다
- **해결**: RecordAccumulator drain 조건 분석 후 `linger.ms=100`, `batch.size=32KB`, `compression.type=lz4` 적용
- **결과**: records/request 6.3→64.3 (+920%) / request_rate 42→8.9/s (-79%) / 압축률 49%
- **트레이드오프**: `linger.ms=100`의 전송 지연은 Kafka 경로(수익률 계산용)에만 해당. UI 시세는 Redis Pub/Sub로 별도 전달되므로 체감 영향 없음
- → [`docs/kafka-producer-batching-tuning.md`](docs/kafka-producer-batching-tuning.md)

### 2. Leader Failover 무손실 검증

- **구성**: RF=3, minISR=2, acks=all, idempotent producer
- **검증**: 3 broker KRaft 클러스터에서 leader를 강제 삭제
- **결과**: retry 35건 발생했지만 idempotent producer로 중복 방지, 메시지 유실 없이 복구 확인
- → [`docs/kafka-leader-failover-verification.md`](docs/kafka-leader-failover-verification.md)

### 3. HikariCP Connection Leak — 증상이 아니라 원인 추적

- **증상**: 운영 로그에서 connection leak 경고 반복
- **원인**: 실제 leak이 아님. 하나의 `@Transactional` 안에서 외부 HTTP API 호출 + 대량 계산이 함께 수행 → 커넥션 점유 시간만 길어진 것
- **해결**: 트랜잭션 경계를 작업 단위로 분리 + 같은 Bean 내부 호출의 AOP 프록시 우회를 피하기 위해 `TxHelper` 별도 Bean 분리
- → [`docs/devlog/connection-leak-fix.md`](docs/devlog/connection-leak-fix.md)

### 4. 유저 PK UUID → Long + Redis 매핑 캐시

- **문제**: 4천만 건 테이블에서 UUID PK의 InnoDB Clustered Index 파편화 — 랜덤 삽입으로 page fill rate 48%, 분할 7회
- **해결**: PK를 Long(auto increment)으로 전환하되, 클라이언트에는 UUID 유지. Redis에 `<UUID, Long>` 매핑을 INSERT 시점에 동시 등록 → UUID Index 탐색 자체를 제거
- **결과**: Pages Accessed 33% 감소 / 인덱스 크기 13~28% 감소 / Insert Latency 5.2% 개선

### 5. KIS API 의존성 분리 — 테스트 환경 구축

- **문제**: 장 마감(15시) 후 실시간 시세를 받을 수 없어 부하 테스트 불가
- **해결**: 시세 공급 계층을 `MarketDataProvider` 인터페이스로 추상화. 테스트 환경에서 `TestProvider`가 설정 가능한 RPS로 시세 발행
- k6 + Grafana 기반 부하 테스트 설계 (WebSocket, Redis hotkey, mixed spike 등 시나리오별 분리)

---

## 관측성

```text
Metrics: Spring Boot → Micrometer → Prometheus → Grafana
Logs:    JSON log → Grafana Alloy → Loki → Grafana
```

→ [`docs/observability.md`](docs/observability.md)

## CI/CD

GitHub Actions → Docker build/push → Manifest repo image tag 갱신 → ArgoCD sync (GitOps)

## 로컬 실행

```bash
docker compose -f infra/docker-compose.yml up -d   # 인프라
./gradlew bootRun                                    # 실행
```
