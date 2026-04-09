# k6 Load Test

운영 서버용 1차 조회 중심 부하테스트 스크립트다. 기본 설계는 `read-only`, `login bootstrap`, `scenario split`이다.

## Scope

포함:
- 공개 시장 조회
- 공개 뉴스 조회
- 인증 기반 내 정보/자산 조회
- 인증 기반 활동/게이미피케이션 조회
- 분리된 고비용 조회

제외:
- 회원가입/토큰 갱신
- 생성/수정/삭제/좋아요 토글
- `/dev/**`
- `main.js` 기준 WebSocket
- LLM/배치/관리 API

## WebSocket Entry Points

WebSocket 부하는 `main.js`가 아니라 별도 entrypoint를 사용한다.

- `ws-main.js`: 기존 WebSocket quote/connect 시나리오 (`k6/ws` 기반)
- `ws-connect-dense.js`: 1개 VU가 여러 WebSocket 연결을 동시에 열어 로컬 k6 발생기 한계를 낮춘 고밀도 connect 전용 시나리오 (`k6/websockets` 기반)

10,000 연결 검증에서 `ws-connect-10k`가 `10,000 VU = 10,000 연결` 모델이라 로컬 `pthread_create failed`에 먼저 걸릴 수 있으므로, 연결 수용만 확인할 때는 `ws-connect-dense.js`를 우선 사용한다.

예시:

```bash
set -a
. .env
. k6/.env.ws-connect-dense-10k
set +a

k6 run k6/ws-connect-dense.js
```

선발급 토큰이 있으면 bootstrap login 병목을 피할 수 있다.

```bash
WS_PREISSUED_TOKENS_JSON='["token-1","token-2"]' \
k6 run k6/ws-connect-dense.js
```

## Files

- `main.js`: k6 entrypoint
- `lib/`: 공통 설정, 데이터 로딩, HTTP wrapper, 메트릭
- `scenarios/`: API 그룹별 플로우
- `data/*.sample.json`: 샘플 토큰/ID 파일 포맷

## Required Environment Variables

- `BASE_URL`: 테스트 대상 서버 주소

## Optional Environment Variables

- `LOAD_PROFILE`: `quick`, `smoke`, `ramp10`, `baseline`, `stepup` 중 하나. 기본값 `smoke`
- `TOKENS_FILE`: 로그인 credential JSON 파일 경로. 기본값 `./k6/data/tokens.sample.json`
- `IDS_FILE`: ID JSON 파일 경로. 기본값 `./k6/data/ids.sample.json`
- `HTTP_TIMEOUT`: 요청 timeout. 기본값 `10s`

## Data File Format

`tokens.json` (`credentials` 포맷)

```json
{
  "credentials": [
    { "loginId": "load-user-1", "password": "password-1" },
    { "loginId": "load-user-2", "password": "password-2" }
  ]
}
```

`ids.json`

```json
{
  "stockIds": [1, 2, 3],
  "newsIds": [101, 102, 103],
  "categoryIds": [1, 2, 3],
  "userIds": ["00000000-0000-0000-0000-000000000001"],
  "portfolioIds": [1, 2],
  "tradeIds": [1001, 1002]
}
```

## Recommended Start

운영 직접 테스트 1차 권장 순서:

1. `smoke`
2. `ramp10`
3. `baseline`
4. `stepup`

첫 실행은 `smoke`로 시작하고, 애플리케이션/DB/Redis/Kafka 메트릭을 같이 본다.

## Example Commands

```bash
BASE_URL=https://api.example.com \
TOKENS_FILE=./k6/data/tokens.json \
IDS_FILE=./k6/data/ids.json \
LOAD_PROFILE=smoke \
k6 run k6/main.js
```

```bash
BASE_URL=https://api.example.com \
TOKENS_FILE=./k6/data/tokens.json \
IDS_FILE=./k6/data/ids.json \
LOAD_PROFILE=baseline \
k6 run k6/main.js
```

## Scenario Mix

- `public_market_read`: 시장 메인 플로우
- `public_news_read`: 뉴스 메인 플로우
- `auth_profile_read`: 내 정보/자산 조회
- `auth_activity_read`: 거래/게이미피케이션 조회
- `heavy_read_isolated`: 캔들/큰 페이지 조회

## Threshold Defaults

- 전체 실패율 `< 2%`
- 공개 조회 p95 `< 700ms`
- 인증 조회 p95 `< 900ms`
- 고비용 조회 p95 `< 1500ms`
- 공개/인증 체크 성공률 `> 99%`

## Operational Notes

- 본 스크립트는 `setup()`에서 `POST /auth/login`으로 토큰을 선발급한다.
- 운영에서는 반드시 테스트 전용 사용자(credential 파일)와 사전 준비된 데이터 풀을 사용한다.
- 401/404는 별도 메트릭으로 집계되므로, 데이터 품질 문제와 서버 오류를 분리해서 볼 수 있다.
- 쓰기 API를 포함하려면 별도 시나리오를 추가하고, 운영 영향도를 다시 산정해야 한다.
