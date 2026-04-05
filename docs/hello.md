원택
0wonteak
코딩

천명현 — 2026. 3. 26. 오후 12:15
그 웹소켓 리스너 노드들은 힙메모 리 한 2기가씩 주고
여러개 띄우면 될듯
원택 — 2026. 3. 26. 오후 12:16
굿굿 max 걸어놓으면 되자나
천명현 — 2026. 3. 26. 오후 12:16
얍
원택 — 2026. 3. 26. 오후 1:42
몇시에볼까??
천명현 — 2026. 3. 26. 오후 1:42
저번처럼 ㄱㄱ?
원택 — 2026. 3. 26. 오후 1:42
6시??
천명현 — 2026. 3. 26. 오후 1:42
얍
원택 — 2026. 3. 26. 오후 1:42
오키오키
천명현 — 오후 8:27
# PriceCandle 데이터 구조 보고서

## 개요

`PriceCandle`은 시장 캔들 데이터를 표현하는 핵심 엔티티다. 종목(`stockId`), 타임프레임(`timeframe`), 기준 시각(`at`)을 비즈니스 식별자로 삼고, OHLCV와 거래대금, 전일 대비 등락률, 결측 여부(`isMissing`)를 함께 저장한다.

이 구조는 `market` 모듈 안에서 조회 캐시이자 외부 시세 API의 영속화 대상 역할을 동시에 맡고 있다. 특히 "이미 조회했지만 데이터가 없었던 시각"까지 `isMissing=true` 레코드로 저장하는 점이 설계의 핵심이다.

---

## 핵심 구조

### 1. 엔티티 정의

정의 위치:

- `src/main/java/depth/finvibe/modules/market/domain/PriceCandle.java`

주요 필드:

| 필드 | 타입 | 의미 |
|---|---|---|
| `id` | `Long` | JPA 기술 PK |
| `stockId` | `Long` | 종목 식별자 |
| `timeframe` | `Timeframe` | 분/일/주/월/년 단위 |
| `at` | `LocalDateTime` | 캔들 시작 기준 시각 |
| `isMissing` | `Boolean` | 실제 캔들 부재 여부 |
| `open` / `high` / `low` / `close` | `BigDecimal` | OHLC 가격 |
| `prevDayChangePct` | `BigDecimal` | 전일 대비 등락률 |
| `volume` | `BigDecimal` | 거래량 |
| `value` | `BigDecimal` | 거래대금 |

구조적 특징:

- 테이블명은 `price_candle`이다.
- `(stock_id, timeframe, at)` 유니크 제약으로 동일 캔들의 중복 적재를 막는다.
- `(stock_id, timeframe, at)` 인덱스로 구간 조회 패턴을 최적화한다.
- `equals` / `hashCode`는 기술 PK가 아니라 `stockId`, `timeframe`, `at`를 기준으로 동작한다.
- 실제 데이터 생성은 `create(...)`, 결측 슬롯 생성은 `createMissing(...)` 팩토리 메서드로 나뉜다.

### 2. 관련 구조와 경계

| 역할 | 위치 | 설명 |
|---|---|---|
| API 응답 DTO | `src/main/java/depth/finvibe/modules/market/dto/PriceCandleDto.java` | `PriceCandle`을 외부 응답 형태로 변환 |
| 조회 유스케이스 | `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java` | DB 조회, 결측 계산, 외부 fetch, 저장, 응답 변환 담당 |
| 영속 포트 | `src/main/java/depth/finvibe/modules/market/application/port/out/PriceCandleRepository.java` | 캔들 저장/조회 추상화 |
| JPA 구현체 | `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleRepositoryImpl.java` | 포트를 JPA에 연결 |
| 외부 시세 포트 | `src/main/java/depth/finvibe/modules/market/application/port/out/RealMarketClient.java` | 외부 캔들 fetch 계약 |
| 외부 시세 구현 | `src/main/java/depth/finvibe/modules/market/infra/client/RealMarketClientImpl.java` | KIS API 호출 후 캔들 DTO 생성 |
| 지수 캐시 서비스 | `src/main/java/depth/finvibe/modules/market/application/IndexMinuteCandleCacheService.java` | 지수 분봉을 `PriceCandle`로 저장 |

### 3. 시간 정규화 규칙

정의 위치:

- `src/main/java/depth/finvibe/modules/market/domain/enums/Timeframe.java`

규칙 요약:

- `MINUTE`는 초/나노초를 0으로 맞춘다.
- `DAY`는 자정, `WEEK`는 해당 주 월요일 00:00, `MONTH`는 해당 월 1일 00:00, `YEAR`는 해당 연도 1월 1일 00:00으로 정규화한다.
- `lastCompletedTime(...)`는 `MINUTE`에서는 직전 1분, `DAY/WEEK/MONTH/YEAR`에서는 직전 완료 기간의 시작 시각을 반환한다.

즉, `PriceCandle.at`은 "종가 시각"이 아니라 "해당 캔들의 시작 시각" 개념에 가깝다.

---

## 데이터 생명주기

### 1. 진입: API 요청 또는 내부 캐시 적재

#### 종목 캔들 조회 경로

- 진입점: `src/main/java/depth/finvibe/modules/market/api/external/MarketController.java`
- 엔드포인트: `GET /market/stocks/{stockId}/candles`
- 컨트롤러는 시작/종료 시각 유효성을 검사하고, `timeframe` 기준으로 종료 시각을 "마지막 완료 캔들" 범위 안으로 보정한다.

#### 지수 분봉 캐시 경로

- 진입 서비스: `src/main/java/depth/finvibe/modules/market/application/IndexMinuteCandleCacheService.java`
- 이 서비스는 지수 시세 스냅샷을 읽어 분봉 `PriceCandle`로 변환한 뒤 저장한다.

### 2. 조회 전 정규화와 락 획득

`MarketQueryService.getStockCandles(...)`는 다음 순서로 동작한다.

1. `stockId + timeframe` 조합으로 분산 락을 건다.
2. `Timeframe.normalizeStart(...)`, `normalizeEnd(...)`로 요청 구간을 캔들 경계에 맞춘다.
3. 정규화된 범위로 기존 `PriceCandle`을 먼저 조회한다.

이 단계의 핵심은 동일 종목/타임프레임에 대한 중복 fetch 및 중복 저장을 줄이는 것이다.

### 3. 결측 구간 계산

`MarketQueryService.calculateMissingCandleTimes(...)`는 "이 범위에 있어야 하는 모든 캔들 시각"을 생성한 뒤, DB에 이미 존재하는 시각을 제거한다.

여기서 중요한 점은 DB에 `isMissing=true`로 저장된 레코드도 "이미 확인한 슬롯"으로 간주된다는 것이다. 즉, 과거에 외부 API에서 비어 있었던 슬롯뿐 아니라, 산술적으로 생성된 시간 슬롯 중 실제 응답이 없었던 구간도 다음 조회 때 다시 fetch 대상이 되지 않는다.

### 4. 외부 데이터 수집
... (276줄 남음)

price-candle-data-structure-report.md
22KB
﻿
천명현
cmh1448
 