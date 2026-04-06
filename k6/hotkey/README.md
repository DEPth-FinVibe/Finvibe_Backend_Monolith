# k6 Hotkey Test

`k6/hotkey` 패키지는 **집중 조회(hot key) 상황을 별도 검증**하기 위한 전용 패키지다.

- 트랙 1: **WebSocket subscribe-init pressure**
  - 목적: 동일 `quote:<stockId>` 토픽으로 subscribe가 몰릴 때 ack/first-event 지연과 실패율을 관찰
- 트랙 2: **Redis current-price cache read hotkey**
  - 목적: 동일 stockId의 현재가를 반복 조회할 때 cache-read latency와 실패율을 관찰
- 트랙 3: **Redis mixed spike**
  - 목적: current-price 반복 조회(read)와 websocket churn(write-ish pressure)을 동시에 올려 단일 Redis 서버의 지연/실패 징후를 관찰
- 비목적: 기존 `k6/ws-main.js` 처럼 steady-state fanout throughput/lag를 주지표로 보는 테스트

기존 WebSocket 테스트(`k6/ws-main.js`)는 그대로 유지하고, 이 패키지는 아래 둘을 분리해서 본다.

- subscribe 초기 진입 병목
- current-price cache 반복 조회 hotkey 병목

## Files

- `main.js`: hotkey 전용 entrypoint (`setup()`에서 기존 login bootstrap 재사용)
- `lib/config.js`: subscribe-init / cache-read profile 및 threshold
- `lib/metrics.js`: hotkey 진단용 메트릭
- `scenarios/ws-hotkey-subscribe.js`: subscribe-init 집중/분산/churn 시나리오
- `scenarios/http-hotkey-cache-read.js`: current-price cache 반복 조회 시나리오
- `.env.redis-spike-*`: read + websocket churn 혼합 spike 프로파일
- `.env.hotkey-*`: subscribe-init 프로파일
- `.env.hotkey-cache-*`: cache-read 프로파일

## Required Environment Variables

- `BASE_URL`: 테스트 대상 서버 주소

## Optional Environment Variables

- `HOTKEY_LOAD_PROFILE`: `hotkey-smoke`, `hotkey-ramp`, `hotkey-stress`, `hotkey-cache-smoke`, `hotkey-cache-ramp`, `hotkey-cache-stress` (기본 `hotkey-smoke`)
- `HOTKEY_SCENARIO`: `hot-key`, `baseline`, `churn` (기본 `hot-key`)
- `HOTKEY_STOCK_ID`: 집중 대상 종목 ID (미지정 시 `IDS_FILE.stockIds` 풀 첫 ID 사용)
- `HOTKEY_DISTRIBUTED_TOPIC_COUNT`: baseline 모드에서 subscribe할 분산 topic 수 (기본 10)
- `HOTKEY_CHURN_ROUNDS`: churn 모드 subscribe/unsubscribe 반복 횟수 (기본 3)
- `HOTKEY_HOLD_MS`: subscribe 이후 유지 시간(ms, 기본 2000)
- `HOTKEY_WAIT_TIMEOUT_MS`: snapshot 대기 timeout(ms, 기본 8000)
- `TOKENS_FILE`: credential JSON 파일 경로 (기존 `.env.ws-*`와 동일하게 `../data/tokens.json` 사용, loader는 `k6/data/tokens.json` 형식도 허용)
- `IDS_FILE`: ID JSON 파일 경로 (기존 `.env.ws-*`와 동일하게 `../data/ids.json` 사용, loader는 `k6/data/ids.json` 형식도 허용)

## Hotkey Metrics

### subscribe-init track

- `ws_hotkey_subscribe_ack_latency_ms`: subscribe ack 도착 지연
- `ws_hotkey_initial_snapshot_latency_ms`: subscribe 이후 first event(또는 explicit initial event) 도착 지연
- `ws_hotkey_connect_rate`, `ws_hotkey_auth_rate`: 연결/인증 성공률
- `ws_hotkey_auth_fail_count`, `ws_hotkey_connect_fail_count`: 인증/연결 실패
- `ws_hotkey_subscribe_fail_count`, `ws_hotkey_rejected_topic_count`: subscribe 실패/거절 topic
- `ws_hotkey_snapshot_miss_count`: timeout 내 첫 이벤트 미도착 수
- `ws_hotkey_disconnect_count`: 세션 종료 수(옵션성 disconnect 신호)

### cache-read track

- `hotkey_cache_read_latency_ms`: `/market/stocks/{stockId}/current-price` 반복 조회 지연
- `hotkey_cache_read_rate`: cache-read 요청 성공률
- `hotkey_cache_read_fail_count`: cache-read 요청 실패 수

### redis-spike track

- `hotkey_cache_read_*`: Redis current-price read pressure 결과
- `ws_hotkey_*`: websocket churn을 통한 watcher/write-ish pressure 결과
- `market.current_price.cache.requests{result=*}`: 백엔드 Micrometer hit/miss/fallback 계측(서버 관측 필요)

## Profiles

- `hotkey-smoke`: subscribe-init sanity, 3 VU 고정, 30초
- `hotkey-ramp`: subscribe-init ramp, 10 → 50 → 120 VU, 15분
- `hotkey-stress`: subscribe-init stress, 20 → 120 → 300 → 500 → 700 VU, 16분
- `hotkey-cache-smoke`: cache-read sanity, 5 VU 고정, 30초
- `hotkey-cache-ramp`: cache-read ramp, 10 → 50 → 120 VU, 15분
- `hotkey-cache-stress`: cache-read stress, 20 → 120 → 300 → 500 → 700 VU, 16분
- `redis-spike-smoke`: mixed sanity, read 5 VU + churn 5 VU, 45초
- `redis-spike-ramp`: mixed ramp, read 10 → 80 → 200 / churn 5 → 50 → 120, 8분
- `redis-spike-stress`: mixed stress, read 20 → 150 → 350 → 500 / churn 10 → 80 → 200 → 300, 8분

`hotkey-smoke`는 성능 상한 측정보다 **구독 흐름 정상성(connect/auth/subscribe/snapshot miss 없음)** 확인에 맞춘 sanity 프로파일이다.

`hotkey-cache-smoke`는 **같은 stockId 반복 조회 시 current-price cache-read 지연이 500ms 안으로 들어오는지** 확인하는 sanity 프로파일이다.

`redis-spike-*`는 단일 Redis 서버에 read + write-ish pressure를 함께 밀어 넣어,
- current-price read latency 급등
- websocket churn 실패 증가
- cache hit/miss 비율 변화
- Redis OOM/eviction/latency monitor 경고
같은 징후를 외부 모니터링과 함께 보는 용도다.

## Example Commands

### 1) subscribe-init direct run

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-smoke
set +a

HOTKEY_SCENARIO=hot-key \
HOTKEY_STOCK_ID=5930 \
k6 run k6/hotkey/main.js
```

### 2) cache-read direct run

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-cache-smoke
set +a

HOTKEY_SCENARIO=hot-key \
HOTKEY_STOCK_ID=5930 \
k6 run k6/hotkey/main.js
```

### 3) subscribe-init baseline(분산 topic) 비교

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-ramp
set +a

HOTKEY_SCENARIO=baseline \
HOTKEY_DISTRIBUTED_TOPIC_COUNT=10 \
k6 run k6/hotkey/main.js
```

### 4) subscribe-init churn 비교

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-smoke
set +a

HOTKEY_SCENARIO=churn \
HOTKEY_CHURN_ROUNDS=5 \
k6 run k6/hotkey/main.js
```

### 5) mixed Redis spike direct run

```bash
set -a
. .env
. k6/hotkey/.env.redis-spike-smoke
set +a

HOTKEY_STOCK_ID=5930 \
k6 run k6/hotkey/main.js
```

## Runner Integration

`k6/run.sh`에 `Hotkey 테스트` 메뉴가 추가되어 아래를 선택 실행할 수 있다.

- subscribe-init hotkey
- cache-read hotkey
- redis mixed spike

## Reporting

hotkey 실행 결과는 `k6/hotkey/reports/` 아래로 따로 저장된다.

- summary JSON: `k6/hotkey/reports/`
- AI 보고서 markdown: summary JSON과 같은 디렉터리
- 일반 REST / 기존 WebSocket 테스트는 계속 `k6/reports/`를 사용한다.
- `SUMMARY_OUTPUT_FILE`을 직접 지정하면 원하는 경로로 override 가능
