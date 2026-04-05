# k6 WebSocket Hotkey Test

`k6/hotkey` 패키지는 **웹소켓 subscribe 시점 hot-key 재현** 전용이다.

- 목적: 동일 `quote:<stockId>` 토픽으로 subscribe가 몰릴 때 ack/snapshot 지연과 실패율을 관찰
- 비목적: 기존 `k6/ws-main.js` 처럼 steady-state fanout throughput/lag를 주지표로 보는 테스트

기존 WebSocket 테스트(`k6/ws-main.js`)는 그대로 유지하고, 이 패키지는 subscribe-time 병목 가설을 분리해서 본다.

## Files

- `main.js`: hotkey 전용 entrypoint (`setup()`에서 기존 login bootstrap 재사용)
- `lib/config.js`: hotkey profile/threshold/runtime 옵션
- `lib/metrics.js`: hotkey 진단용 메트릭
- `scenarios/ws-hotkey-subscribe.js`: subscribe 집중/분산/churn 시나리오
- `.env.hotkey-*`: 실행 프로파일

## Required Environment Variables

- `BASE_URL`: 테스트 대상 서버 주소

## Optional Environment Variables

- `HOTKEY_LOAD_PROFILE`: `hotkey-smoke`, `hotkey-ramp`, `hotkey-stress` (기본 `hotkey-smoke`)
- `HOTKEY_SCENARIO`: `hot-key`, `baseline`, `churn` (기본 `hot-key`)
- `HOTKEY_STOCK_ID`: 집중 대상 종목 ID (미지정 시 `IDS_FILE.stockIds` 풀 첫 ID 사용)
- `HOTKEY_DISTRIBUTED_TOPIC_COUNT`: baseline 모드에서 subscribe할 분산 topic 수 (기본 10)
- `HOTKEY_CHURN_ROUNDS`: churn 모드 subscribe/unsubscribe 반복 횟수 (기본 3)
- `HOTKEY_HOLD_MS`: subscribe 이후 유지 시간(ms, 기본 2000)
- `HOTKEY_WAIT_TIMEOUT_MS`: snapshot 대기 timeout(ms, 기본 8000)
- `TOKENS_FILE`: credential JSON 파일 경로 (기존 `.env.ws-*`와 동일하게 `../data/tokens.json` 사용, loader는 `k6/data/tokens.json` 형식도 허용)
- `IDS_FILE`: ID JSON 파일 경로 (기존 `.env.ws-*`와 동일하게 `../data/ids.json` 사용, loader는 `k6/data/ids.json` 형식도 허용)

## Hotkey Metrics

- `ws_hotkey_subscribe_ack_latency_ms`: subscribe ack 도착 지연
- `ws_hotkey_initial_snapshot_latency_ms`: subscribe 이후 initial snapshot(`event.data.initial=true`) 도착 지연
- `ws_hotkey_connect_rate`, `ws_hotkey_auth_rate`: 연결/인증 성공률
- `ws_hotkey_auth_fail_count`, `ws_hotkey_connect_fail_count`: 인증/연결 실패
- `ws_hotkey_subscribe_fail_count`, `ws_hotkey_rejected_topic_count`: subscribe 실패/거절 topic
- `ws_hotkey_snapshot_miss_count`: timeout 내 initial snapshot 미도착 수
- `ws_hotkey_disconnect_count`: 세션 종료 수(옵션성 disconnect 신호)

## Profiles

- `hotkey-smoke`: 10 VU 고정, 5분
- `hotkey-ramp`: 10 → 50 → 120 VU, 15분
- `hotkey-stress`: 20 → 120 → 300 → 500 → 700 VU, 16분

## Example Commands

### 1) direct run (권장 기본)

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-smoke
set +a

HOTKEY_SCENARIO=hot-key \
HOTKEY_STOCK_ID=5930 \
k6 run k6/hotkey/main.js
```

### 2) baseline(분산 topic) 비교

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-ramp
set +a

HOTKEY_SCENARIO=baseline \
HOTKEY_DISTRIBUTED_TOPIC_COUNT=10 \
k6 run k6/hotkey/main.js
```

### 3) churn 비교

```bash
set -a
. .env
. k6/hotkey/.env.hotkey-smoke
set +a

HOTKEY_SCENARIO=churn \
HOTKEY_CHURN_ROUNDS=5 \
k6 run k6/hotkey/main.js
```

## Runner Integration

`k6/run.sh`에 `Hotkey WebSocket 테스트` 메뉴가 추가되어 기존 방식처럼 profile 선택 실행이 가능하다.

## Reporting

hotkey 실행 결과는 `k6/hotkey/reports/` 아래로 따로 저장된다.

- summary JSON: `k6/hotkey/reports/`
- AI 보고서 markdown: summary JSON과 같은 디렉터리
- 일반 REST / 기존 WebSocket 테스트는 계속 `k6/reports/`를 사용한다.
- `SUMMARY_OUTPUT_FILE`을 직접 지정하면 원하는 경로로 override 가능
