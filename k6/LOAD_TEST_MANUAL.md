# Finvibe k6 부하테스트 매뉴얼

이 문서는 `k6/` 폴더의 실제 스크립트(`main.js`, `lib/*`, `scenarios/*`)를 기준으로 작성한 실행 매뉴얼이다.

## 1. 목적과 범위

### 포함 범위 (조회 중심)
- 공개 시장 조회
- 공개 뉴스/테마 조회
- 인증 기반 내 정보/자산 조회
- 인증 기반 활동/게이미피케이션 조회
- 고비용 조회 시나리오 분리 테스트

### 제외 범위
- 로그인/회원가입/토큰 갱신
- 쓰기 API(생성/수정/삭제/좋아요 토글)
- `/dev/**`, WebSocket, 배치/관리 API

## 2. 폴더 구조

```text
k6/
  main.js
  lib/
    auth.js
    checks.js
    config.js
    data.js
    http.js
  scenarios/
    public-market.js
    public-news.js
    auth-profile.js
    auth-activity.js
    heavy-read.js
```

## 3. 사전 준비

### 3.1 도구 설치
- `k6` CLI 설치 필요
- macOS(Homebrew) 예시:

```bash
brew install k6
k6 version
```

### 3.2 테스트 데이터 준비
`k6/lib/data.js` 기준으로 아래 파일을 준비한다.
- `TOKENS_FILE` (인증 시나리오용)
- `IDS_FILE` (stock/news/category/user/portfolio/trade ID 풀)

기본 경로는 다음과 같지만, 현재 레포에는 `k6/data/` 디렉터리가 없을 수 있으므로 직접 생성/지정하는 것을 권장한다.
- 기본 토큰 경로: `./k6/data/tokens.sample.json`
- 기본 ID 경로: `./k6/data/ids.sample.json`

예시:

```bash
mkdir -p k6/data
```

`k6/data/tokens.json`

```json
{
  "tokens": [
    "access-token-1",
    "access-token-2"
  ]
}
```

`k6/data/ids.json`

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

## 4. 환경변수

### 필수
- `BASE_URL`: 테스트 대상 API 베이스 URL

### 선택
- `LOAD_PROFILE`: `quick | smoke | ramp10 | baseline | stepup` (기본값 `smoke`)
- `TOKENS_FILE`: 토큰 JSON 경로 (기본값 `./k6/data/tokens.sample.json`)
- `IDS_FILE`: ID JSON 경로 (기본값 `./k6/data/ids.sample.json`)
- `HTTP_TIMEOUT`: 기본 요청 타임아웃 (기본값 `10s`)

## 5. 실행 방법

프로젝트 루트(`Finvibe_Backend_Monolith`)에서 실행:

```bash
BASE_URL=https://api.example.com \
TOKENS_FILE=./k6/data/tokens.json \
IDS_FILE=./k6/data/ids.json \
LOAD_PROFILE=smoke \
k6 run k6/main.js
```

권장 순서:
1. `smoke`
2. `ramp10`
3. `baseline`
4. `stepup`

## 6. 프로파일 상세

`k6/lib/config.js` 기준.

### smoke (5분, 저부하 검증)
- `public_market_read`: 2 req/s
- `public_news_read`: 1 req/s
- `auth_profile_read`: 1 req/s
- `auth_activity_read`: 1 req/s
- `heavy_read_isolated`: 0.1 req/s (`1/10s`)

### ramp10 (10분, 중간 부하)
- `public_market_read`: 3 → 5 → 6 req/s
- `public_news_read`: 1 → 2 → 3 req/s
- `auth_profile_read`: 1 → 2 → 2 req/s
- `auth_activity_read`: 1 → 1 → 2 req/s
- `heavy_read_isolated`: 0.125 req/s (`1/8s`, 10분)

### baseline (약 25분, 기준 부하)
- `public_market_read`: 4 → 8 → 10 req/s
- `public_news_read`: 2 → 4 → 5 req/s
- `auth_profile_read`: 2 → 4 → 4 req/s
- `auth_activity_read`: 1 → 2 → 2 req/s
- `heavy_read_isolated`: 0.2 req/s (`1/5s`, 20분)

### stepup (약 25분, 상향 부하)
- `public_market_read`: 8 → 16 → 22 req/s
- `public_news_read`: 4 → 8 → 12 req/s
- `auth_profile_read`: 3 → 7 → 10 req/s
- `auth_activity_read`: 2 → 3 → 4 req/s
- `heavy_read_isolated`: 약 0.33 req/s (`1/3s`, 25분)

## 7. 시나리오별 API 흐름

### 7.1 public_market_read
- `/market/status`
- `/market/stocks/top-by-value`
- `/market/stocks/{stockId}`
- `/market/stocks/{stockId}/candles` (최근 7일)
- `/market/categories`
- `/market/categories/{categoryId}/stocks`

### 7.2 public_news_read
- `/news?page=0&size=20&sortType=LATEST|POPULAR`
- `/news/{newsId}`
- `/news/keywords/trending`
- `/themes/today`
- `/themes/today/{categoryId}` (`200`, `404` 허용)

### 7.3 auth_profile_read
- `/members/me`
- `/members/favorite-stocks`
- `/wallets/balance`
- `/portfolios`
- `/assets/top-100` (`200`, `204` 허용)
- `/rankings/user-profit`

### 7.4 auth_activity_read
- `/trades/history?year&month`
- `/trades/reserved/stock-ids`
- `/trades/users/{userId}/history?year&month` (`200`, `403`, `404` 허용)
- `/xp/me`
- `/xp/squads/ranking`
- `/xp/squads/contributions/me`
- `/badges/me`
- `/challenges/me`
- `/challenges/completed?year&month`
- `/squads`
- `/squads/me` (`200`, `404` 허용)

### 7.5 heavy_read_isolated
- `/market/stocks/{stockId}/candles` (최근 90일, timeout 20s)
- `/market/indexes/KOSPI/candles` (최근 2일, timeout 20s)
- `/news?page=0~4&size=50&sortType=POPULAR` (timeout 15s)
- `/news/{newsId}` (timeout 15s)

## 8. 인증 토큰 동작 주의사항

- 인증 시나리오(`auth_*`)는 토큰이 없으면 즉시 에러로 실패한다.
  - 에러 메시지: `Auth scenario requires at least one token in TOKENS_FILE`
- `heavy_read_isolated`는 토큰이 없어도 동작하도록 구현되어 있다(있으면 `Authorization` 헤더 사용).

## 9. 기본 임계치(Threshold)

`k6/lib/config.js` 기준:
- `http_req_failed`: `rate < 0.02`
- `http_req_duration`: `p(95) < 1000ms`, `p(99) < 2000ms`
- `checks{scope:public}`: `rate > 0.99`
- `checks{scope:auth}`: `rate > 0.99`
- `checks{cost:heavy}`: `rate > 0.95`
- `http_req_duration{scope:public}`: `p(95) < 700ms`
- `http_req_duration{scope:auth}`: `p(95) < 900ms`
- `http_req_duration{cost:heavy}`: `p(95) < 1500ms`

## 10. 결과 해석 포인트

- `401`은 `unauthorized_rate`로 별도 집계됨
  - 주로 토큰 만료/권한 오류/잘못된 환경 분리 문제를 의미
- `404`는 `not_found_rate` + `api_business_failure_count`로 집계됨
  - 주로 테스트 데이터(ID 풀) 품질 이슈를 의미
- 즉, `5xx`/타임아웃 같은 서버 오류와 데이터 품질 문제를 분리해서 해석해야 한다.

## 11. 운영 실행 체크리스트

1. 운영과 분리된 테스트 전용 계정/토큰 준비
2. `IDS_FILE` 내 ID가 실제 존재하는지 사전 검증
3. `smoke`로 연결/권한/데이터 품질 먼저 검증
4. `ramp10`으로 단기 중간부하 확인
5. `baseline` 실행 중 API/DB/Redis/Kafka 모니터링 동시 확인
6. 임계치 위반 시, 위반 지표와 scenario tag(`scope`, `module`, `flow`, `cost`) 기준으로 병목 구간 식별
7. `stepup`은 배포 직후가 아닌 안정 구간에서 수행

## 12. 자주 발생하는 실패 원인

- `BASE_URL` 미설정
- `LOAD_PROFILE` 오타 (`quick`, `smoke`, `ramp10`, `baseline`, `stepup`만 허용)
- `TOKENS_FILE` 경로 오류 또는 빈 토큰 배열
- `IDS_FILE` 누락/오타로 인한 404 급증
- 대상 서버 CORS/WAF/Rate Limit 정책 미반영

## 13. 빠른 트러블슈팅 명령 예시

```bash
# 1) 스모크로 설정 검증
BASE_URL=https://api.example.com \
TOKENS_FILE=./k6/data/tokens.json \
IDS_FILE=./k6/data/ids.json \
LOAD_PROFILE=smoke \
k6 run k6/main.js

# 2) 타임아웃 완화 후 재측정
BASE_URL=https://api.example.com \
TOKENS_FILE=./k6/data/tokens.json \
IDS_FILE=./k6/data/ids.json \
LOAD_PROFILE=baseline \
HTTP_TIMEOUT=15s \
k6 run k6/main.js
```
