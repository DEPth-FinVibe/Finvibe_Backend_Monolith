# Redis Single-Node Mixed WebSocket Test Runbook

## 목적

아래 조합을 한 번에 실행해 단일 Redis + listener 2대 환경에서 병목 위치를 확인한다.

1. **k6 mixed websocket 시나리오**
2. **direct Redis price-event publisher**
3. **Grafana dashboard 모니터링**

---

## 구성 요소

### 1. k6 websocket 수요 부하

- 파일: `k6/hotkey/main.js`
- 프로파일: `redis-single-mixed-smoke`, `redis-single-mixed-ramp`, `redis-single-mixed-10k`

### 2. Redis direct publisher

- 파일: `k6/hotkey/tools/redis_price_publisher.py`
- 역할: `market:price-updated` 채널로 가격 이벤트 직접 `PUBLISH`

### 3. Grafana dashboard

- 파일: `../Finvibe-Websocket-Listener-main/docs/monitoring/grafana-redis-single-mixed-dashboard.json`
- import guide: `../Finvibe-Websocket-Listener-main/docs/monitoring/redis-single-mixed-dashboard-import.md`

---

## 전제 조건

### 서비스 측

- listener 2대 이상 기동
- Redis 1대 기동
- Prometheus가 listener + Redis exporter 둘 다 scrape 중
- Grafana datasource 연결 완료

### 테스트 측

- `k6` 설치
- `python3` 설치
- `k6/data/tokens.json`
- `k6/data/ids.json`

---

## Step 1. Grafana 대시보드 import

1. Grafana → **Dashboards** → **New** → **Import**
2. `grafana-redis-single-mixed-dashboard.json` 업로드
3. Prometheus datasource 선택
4. 변수 확인
   - `job`
   - `instance`
   - `redis_job`
   - `redis_instance`

대시보드에서 우선 볼 패널:

- Redis 이벤트 소비 vs 브로드캐스트
- 세션 queue overflow / task failure
- Watcher 작업량(save/renew/remove)
- Redis commands/s
- Redis memory / fragmentation
- Redis network in/out

---

## Step 2. direct Redis publisher 실행

프로젝트 루트(`Finvibe_Backend_Monolith`)에서 실행.

### 예시 A. single-hot steady

```bash
python3 k6/hotkey/tools/redis_price_publisher.py \
  --host 127.0.0.1 \
  --port 6379 \
  --channel market:price-updated \
  --mode single-hot \
  --hot-stock-id 5930 \
  --rate 1000 \
  --duration-sec 600 \
  --stock-pool-file ./k6/data/ids.json
```

### 예시 B. zipf mixed

```bash
python3 k6/hotkey/tools/redis_price_publisher.py \
  --host 127.0.0.1 \
  --port 6379 \
  --channel market:price-updated \
  --mode zipf \
  --hot-stock-id 5930 \
  --hot-ratio 0.5 \
  --zipf-skew 1.1 \
  --rate 2000 \
  --duration-sec 900 \
  --stock-pool-file ./k6/data/ids.json
```

### 예시 C. burst

```bash
python3 k6/hotkey/tools/redis_price_publisher.py \
  --host 127.0.0.1 \
  --port 6379 \
  --channel market:price-updated \
  --mode burst \
  --hot-stock-id 5930 \
  --rate 200 \
  --burst-rate 3000 \
  --burst-start-sec 60 \
  --burst-duration-sec 30 \
  --duration-sec 300 \
  --stock-pool-file ./k6/data/ids.json
```

---

## Step 3. k6 mixed websocket 실행

### 예시 A. ramp

```bash
set -a
. .env
set +a

HOTKEY_LOAD_PROFILE=redis-single-mixed-ramp \
HOTKEY_SCENARIO=mixed \
HOTKEY_STOCK_ID=5930 \
HOTKEY_SESSION_HOLD_MS=300000 \
HOTKEY_MIXED_TOPIC_COUNT=3 \
HOTKEY_MIXED_HOT_RATIO=0.5 \
HOTKEY_MIXED_CHURN_PROBABILITY=0.10 \
HOTKEY_MIXED_CHURN_INTERVAL_MS=30000 \
HOTKEY_MIXED_MAX_CHURN_CYCLES=2 \
SUMMARY_OUTPUT_FILE=./k6/hotkey/reports/redis-single-mixed-ramp.json \
k6 run k6/hotkey/main.js
```

### 예시 B. 10k 유지

```bash
set -a
. .env
set +a

HOTKEY_LOAD_PROFILE=redis-single-mixed-10k \
HOTKEY_SCENARIO=mixed \
HOTKEY_STOCK_ID=5930 \
HOTKEY_SESSION_HOLD_MS=600000 \
HOTKEY_MIXED_TOPIC_COUNT=3 \
HOTKEY_MIXED_HOT_RATIO=0.5 \
HOTKEY_MIXED_CHURN_PROBABILITY=0.15 \
HOTKEY_MIXED_CHURN_INTERVAL_MS=30000 \
HOTKEY_MIXED_MAX_CHURN_CYCLES=3 \
SUMMARY_OUTPUT_FILE=./k6/hotkey/reports/redis-single-mixed-10k.json \
k6 run k6/hotkey/main.js
```

---

## 추천 실험 순서

### 1차: Redis가 먼저 아픈지 확인

- publisher: `single-hot`, `rate=1000`
- k6: `redis-single-mixed-ramp`

### 2차: hot key 강도 증가

- publisher: `single-hot`, `rate=2000`, `rate=5000`
- k6: `redis-single-mixed-ramp` 또는 `redis-single-mixed-10k`

### 3차: 더 현실적인 분포

- publisher: `zipf`, `rate=2000~3000`
- k6: `redis-single-mixed-10k`

### 4차: burst resilience

- publisher: `burst`
- k6: `redis-single-mixed-10k`

---

## Grafana 해석 순서

### 1. Redis가 먼저 흔들리는지

- `Redis commands/s`
- `Redis memory / fragmentation`
- `Redis network in/out`
- `Redis clients / ops processed`

#### 신호

- `publish/sadd/srem/expire` 급증
- Redis CPU/네트워크/latency 먼저 상승

### 2. listener가 먼저 흔들리는지

- `Redis 이벤트 소비 vs 브로드캐스트`
- `세션 queue overflow / task failure`
- `이벤트 전송 실패 원인`

#### 신호

- `redis consumed/s`는 유지되는데 `deliveries/s`가 못 따라감
- `broadcast_event_drop` / `watch_renew_drop` 증가

### 3. renew/churn 영향 분리

- `Watcher 작업량(save/renew/remove)`

#### 신호

- `renew`가 주기적으로 튀면 renew storm
- `save/remove` 증가가 크면 churn pressure

---

## 최종 해석 질문

1. 단일 Redis가 먼저 병목이 되는가?
2. Redis ingress(PUBLISH)와 watcher churn 중 무엇이 더 먼저 아픈가?
3. listener fanout이 먼저 막히는가?
4. hot stock 하나가 전체를 흔드는가?
5. Redis Cluster보다 listener/fanout 개선이 더 시급한가?
