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

외부 fetch는 `RealMarketClientImpl`이 담당한다.

- 분봉(`MINUTE`): KIS 시세 API를 2시간 간격 질의로 반복 호출해 결과를 합친다.
- 일/주/월/년봉: 기간 코드(`D/W/M/Y`) 기반 API를 호출하고, 최대 100건 단위 응답을 역방향으로 넘기며 누적한다.
- 응답은 우선 `PriceCandleDto.Response`로 만들어진다.

추가로 분봉 fetch는 주말은 건너뛰지만, 현재 확인한 코드에서는 `HolidayCalendarService`를 사용해 휴장일을 걸러내지는 않는다. 따라서 평일 휴장일이나 외부 응답이 비어 있는 구간도 `isMissing`으로 기록될 수 있다.

즉, 외부 데이터는 곧바로 엔티티가 아니라 DTO 형태로 애플리케이션 서비스에 전달된다.

### 5. 영속화

`MarketQueryService.saveFetchedAndMissingCandles(...)`는 두 종류의 레코드를 함께 저장한다.

- 실제로 받아온 캔들: `createPriceCandleFrom(...)`로 `PriceCandle` 생성
- 받아오지 못한 슬롯: `PriceCandle.createMissing(...)`로 결측 캔들 생성

이후 `PriceCandleRepository.saveAll(...)`을 통해 배치 저장한다.

지수 분봉 경로에서는 `IndexMinuteCandleCacheService`가 `IndexPriceClient` 응답을 `PriceCandle.create(...)`로 바꾼 뒤, 이미 저장된 시각을 제외하고 저장한다.

### 6. 조회 응답 반환

조회 결과 반환 시에는 `isMissing=true` 레코드를 필터링한다.

- 종목 캔들: 기존 DB 데이터와 새로 fetch한 DTO를 병합 후 시각 오름차순 정렬
- 지수 캔들: DB의 `PriceCandle`을 읽은 뒤 `PriceCandleDto.Response.from(...)`으로 변환

결과적으로 저장소에는 실제 데이터와 결측 마커가 함께 존재하지만, API 응답에는 실제 캔들만 노출된다.

또한 지수 캔들 조회는 cache-read-only에 가깝다. `MarketQueryService.getIndexCandles(...)`는 DB에 저장된 분봉만 읽어 반환하며, 조회 시점에 외부 API를 호출해 누락 구간을 보충하지는 않는다.

### 7. 2차 소비

`PriceCandle` 자체 또는 candle-shaped DTO는 `market` 모듈 안의 다른 모델/흐름에도 재사용된다.

- `src/main/java/depth/finvibe/modules/market/domain/CurrentPrice.java`: `PriceCandle`에서 현재가 형태로 변환 가능
- `src/main/java/depth/finvibe/modules/market/domain/BatchUpdatePrice.java`: `PriceCandleDto.Response`를 배치 업데이트용 도메인 객체로 변환

즉, `PriceCandle`은 단순 차트 응답 모델이 아니라 시장 가격 데이터의 공통 표현 중 하나로 쓰이고 있다.

### 8. 현재 코드에서 보이는 운영 특성

- 종목 캔들은 요청 기반(on-demand)으로 채워진다.
- 지수 분봉은 별도 캐시 서비스가 존재한다.
- `IndexMinuteCandleCacheScheduler` 컴포넌트는 존재하지만, 현재 확인한 코드 범위에서는 `@Scheduled`나 외부 호출 지점이 보이지 않았다.
- `initializeIndexMinuteCandlesIfEmpty(...)`도 현재 확인한 코드 범위에서는 호출처가 없었다.

따라서 지수 분봉 캐시는 설계 의도는 분명하지만, 실제 트리거 wiring은 추가 확인 포인트다.

---

## 설계의 Pros

### 1. 조회 캐시와 원본 저장소 역할을 동시에 수행한다

`price_candle` 테이블은 단순 영속 계층이 아니라 외부 API 호출 결과를 재사용하는 캐시 역할까지 수행한다. 같은 구간을 반복 조회할 때 DB 우선 조회가 가능하므로 외부 시세 API 의존도와 중복 호출 비용을 줄이는 데 유리하다.

### 2. 결측 데이터까지 구조적으로 관리한다

`isMissing` 플래그를 둔 덕분에 "데이터가 없음"도 상태로 저장된다. 이 방식은 동일한 빈 슬롯에 대해 매번 외부 API를 다시 호출하지 않게 해주고, 조회 비용을 안정화한다.

### 3. 시간 정규화 규칙의 중심축이 비교적 명확하다

`Timeframe` enum이 정규화, 다음 시각 계산, 마지막 완료 시각 계산의 공통 기준을 담당한다. `RealMarketClientImpl`에도 보조 정규화 로직이 일부 존재하지만, 전체 시간 경계 해석의 중심축은 `Timeframe`에 모여 있다.

### 4. 중복 저장 방어가 다층으로 깔려 있다

유니크 제약, 인덱스, 분산 락, 기존 시각 조회 후 필터링이 함께 적용된다. 단일 장치에 의존하지 않고 저장 중복을 여러 단계에서 막는 구조다.

### 5. 모듈 경계가 비교적 명확하다

엔티티는 domain, 조회 조합은 application, 저장은 port/out + infra/persistence, 외부 시세는 infra/client, 응답 노출은 dto/api로 분리되어 있다. Spring Boot 모놀리식 구조 안에서도 책임이 비교적 선명하다.

---

## 설계의 Cons

### 1. API DTO가 내부 통합 포맷처럼 사용된다

`RealMarketClient` 포트가 `PriceCandleDto.Response`를 반환하고, `BatchUpdatePrice`도 이 DTO를 입력으로 받는다. 즉, 외부 연동 결과와 내부 애플리케이션 흐름이 API 응답 DTO에 의존하고 있어, 도메인/응답 경계가 다소 흐려져 있다.

### 2. 실제 캔들과 결측 마커가 같은 테이블에 섞여 있다

`isMissing=true` 행은 성능상 이점이 있지만, 동시에 모든 조회에서 "실제 데이터인지"를 의식하게 만든다. 쿼리, 집계, 후속 변환 로직이 이 플래그를 빠뜨리면 의미가 달라질 수 있다는 부담이 있다.

### 3. 엔티티가 식별자만 들고 있어 연관관계 정보가 약하다

`PriceCandle`은 `stockId`만 저장하고 `Stock` 연관 엔티티를 직접 참조하지 않는다. 단순성과 성능 면에서는 이점이 있지만, 도메인 관점에서는 객체 관계 표현력이 낮고, 풍부한 도메인 모델보다는 레코드 저장소에 가깝다.

### 4. 지수 분봉 적재 흐름의 실행 경로가 코드상 명확하지 않다

지수 분봉용 캐시 서비스와 스케줄러 컴포넌트는 존재하지만, 현재 확인한 코드에서는 스케줄 실행 어노테이션이나 초기화 메서드 호출 지점이 보이지 않는다. 설계 의도 대비 운영 wiring이 덜 드러난 상태다.

### 5. 삭제/보관 정책이 구조에서 드러나지 않는다

조회와 저장 경로는 분명하지만, 오래된 캔들 정리나 보관 기간 정책은 현재 확인한 코드 범위에서 드러나지 않았다. 장기간 운영 시 테이블 크기 관리 전략이 별도 설계로 필요할 가능성이 있다.

### 6. 종목 캔들과 지수 캔들의 생성 경로가 비대칭적이다

종목 캔들은 외부 DTO를 거쳐 `MarketQueryService`에서 엔티티로 변환하고, 지수 캔들은 서비스 내부에서 곧바로 `PriceCandle.create(...)`를 사용한다. 결과는 같지만 생성 경로가 일관적이지 않아 향후 필드가 늘어나면 변환 중복이 커질 수 있다.

---

## 성능 개선 관점에서의 분석

### 결론

성능 개선 여지는 분명히 있다. 다만 병목의 중심은 `PriceCandle` 엔티티 자체나 단순 JPA range query보다는, 종목 캔들 조회 시 결측 구간을 계산하고 외부 API로 backfill하는 방식에 있다.

현재 구조는 DB-first 조회 자체는 합리적이지만, 분봉 결측 처리 방식이 실제 필요한 양보다 더 많은 시간 슬롯을 생성하고 저장할 수 있다. 이 때문에 외부 API 호출 수, 락 점유 시간, `price_candle` 테이블 크기, 이후 조회 비용이 함께 커질 가능성이 있다.

### 1. 가장 큰 개선 포인트: 분봉 결측 슬롯 생성 방식

관련 위치:

- `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java`

핵심 메서드:

- `calculateMissingCandleTimes(...)`
- `generateCandleTimesInRange(...)`
- `saveFetchedAndMissingCandles(...)`

현재 로직은 요청한 시작~종료 시각 사이의 모든 minute 슬롯을 산술적으로 생성한다. 하지만 실제 외부 분봉 fetch는 장중 시간대만 응답한다. 따라서 야간, 주말, 휴장일 같은 비거래 시간대도 "결측 후보"가 되고, 최종적으로 `isMissing=true` 행으로 저장될 수 있다.

이 설계는 다음 비용을 유발한다.

- 저장해야 할 row 수 증가
- `price_candle` 테이블 크기 증가
- 이후 같은 기간을 조회할 때 읽어야 할 row 수 증가
- 같은 종목/타임프레임에 대한 락 보유 시간 증가

즉, 현재 성능 이슈의 핵심은 "결측을 저장한다" 자체보다, "무엇을 결측으로 간주하느냐"가 지나치게 넓다는 점에 있다.

### 2. 두 번째 개선 포인트: sparse miss를 넓은 연속 구간으로 backfill하는 방식

관련 위치:

- `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java`

`fetchStockCandlesWithLock(...)`는 누락된 시각들 중 가장 이른 시각과 가장 늦은 시각을 구한 뒤, 그 전체 구간을 한 번에 외부 API에서 다시 가져오도록 설계되어 있다.

문제는 실제 누락이 드문드문 존재하는 sparse miss 상황에서도, 중간 전체 구간이 다시 fetch 대상이 된다는 점이다. 예를 들어 몇 개의 시점만 빠져 있어도, 그 사이 전체 span을 다시 보고, 응답이 없는 나머지 슬롯은 또 `isMissing=true`로 저장하게 된다.

이 구조는 다음 상황에서 특히 비효율적이다.

- 긴 기간을 처음 조회하는 경우
- 일부 구간만 비어 있는 데이터를 다시 조회하는 경우
- 분봉 단위에서 범위가 넓은 요청이 들어오는 경우

따라서 성능 관점에서는 연속 구간 하나로 뭉개는 현재 방식이 실질적인 과잉 작업을 만드는 설계 포인트로 보인다.

### 3. 세 번째 개선 포인트: 외부 분봉 fetch의 동기 fan-out

관련 위치:

- `src/main/java/depth/finvibe/modules/market/infra/client/RealMarketClientImpl.java`
- `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java`

핵심 메서드:

- `RealMarketClientImpl.fetchIntradayCandles(...)`
- `RealMarketClientImpl.buildQueryTimes(...)`
- `RealMarketClientImpl.fetchWithRetry(...)`
- `MarketQueryService.getStockCandles(...)`

분봉 fetch는 거래일별로 순회하고, 각 거래일 안에서 다시 2시간 단위 query time마다 KIS API를 동기적으로 호출한다. 실패 시 재시도와 sleep도 같은 요청 흐름 안에서 수행된다.

문제는 이 과정이 `getStockCandles(...)`의 분산 락 안에서 수행된다는 점이다. 즉, 외부 API가 느릴수록 해당 `stockId + timeframe` 조합 전체의 처리량이 함께 떨어진다.

이 구조의 성능 비용은 다음처럼 나타난다.

- miss가 발생한 요청의 응답시간 증가
- 같은 종목/타임프레임 요청의 직렬화 심화
- 외부 API 지연이 내부 락 경합으로 전파됨

따라서 실무적으로는 DB 조회보다 외부 API fetch와 락 점유의 조합이 더 큰 병목일 가능성이 높다.

### 4. 중간 우선순위의 개선 포인트

#### 배치 insert 효율

관련 위치:

- `src/main/java/depth/finvibe/modules/market/domain/PriceCandle.java`
- `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleRepositoryImpl.java`

`PriceCandle`은 `GenerationType.IDENTITY`를 사용하고, 저장은 `saveAll(...)`에 의존한다. 일반적인 JPA/Hibernate 환경에서는 이 조합이 대량 insert batching 효율을 제한할 수 있다. 다만 이것은 대량 backfill이 실제로 발생할 때 더 크게 드러나는 비용이며, 근본 원인은 앞단에서 너무 많은 row를 만들 수 있다는 점이다.

#### 컬렉션 생성과 정렬 비용

관련 위치:

- `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java`

`mergeAndSortCandles(...)`, `saveFetchedAndMissingCandles(...)`는 여러 차례 리스트/셋 변환과 정렬을 수행한다. 큰 범위 요청에서는 CPU와 메모리 할당 비용이 생길 수 있지만, 외부 API 호출과 대량 row 생성에 비하면 우선순위는 낮다.

### 5. 겉보기보다 덜 중요한 포인트

#### DB range query 자체

관련 위치:

- `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleJpaRepository.java`
- `src/main/java/depth/finvibe/modules/market/domain/PriceCandle.java`

`findByStockIdAndTimeframeAndAtBetweenOrderByAtAsc(...)`의 query shape는 현재 인덱스 `(stock_id, timeframe, at)`와 잘 맞는다. 따라서 조회 쿼리 문장 자체가 비정상적으로 비싼 구조로 보이지는 않는다.

오히려 문제는 이 쿼리가 읽어야 할 row 수다. 특히 비거래 시간대까지 `isMissing` row가 많이 쌓이면, 같은 쿼리라도 점점 무거워질 수 있다.

#### 시간 정규화 로직 자체

관련 위치:

- `src/main/java/depth/finvibe/modules/market/domain/enums/Timeframe.java`

`Timeframe`의 정규화 연산은 자체로는 가볍다. 비용은 이 정규화 규칙을 바탕으로 큰 범위의 시각 리스트를 반복 생성할 때 발생한다.

### 6. 추가로 확인된 correctness + performance 성격의 포인트

관련 위치:

- `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleRepositoryImpl.java`

`findExisting(...)`는 end time 정렬에도 사실상 start 정렬 로직을 재사용하고 있다.

```java
LocalDateTime alignedStartTime = alignStartTime(startTime, timeframe);
LocalDateTime alignedEndTime = alignStartTime(endTime, timeframe);
```

이 부분은 순수한 성능 병목이라기보다는 경계 시각을 덜 정확하게 맞춰 불필요한 miss 계산이나 재조회로 이어질 수 있는 지점이다. 즉, correctness와 성능이 함께 얽힌 보조 이슈로 보는 편이 맞다.

### 7. 우선순위 정리

성능 관점에서의 우선순위는 다음 순서로 보는 것이 타당하다.

1. 분봉 결측 슬롯을 실제 거래 세션 기준으로 더 좁게 정의할 수 있는가
2. sparse miss를 넓은 연속 구간 전체로 backfill하지 않도록 바꿀 수 있는가
3. 외부 분봉 fetch의 동기 fan-out과 락 보유 시간을 줄일 수 있는가
4. 대량 저장 시 insert batching 효율을 높일 수 있는가
5. 메모리 내 리스트/정렬 비용을 줄일 수 있는가

즉, 가장 큰 개선 효과는 쿼리 튜닝보다 "필요 이상으로 생성/저장/fetch하는 데이터를 줄이는 것"에서 나올 가능성이 높다.

---

## 종합 평가

현재의 `PriceCandle` 설계는 "차트 데이터 엔티티"라기보다 "시장 시계열 조회 캐시"에 더 가깝다. 특히 `isMissing` 기반 결측 저장, 분산 락, 시간 정규화, DB 우선 조회 전략은 외부 시세 API 비용과 중복 작업을 줄이기 위한 실용적인 선택으로 보인다.

반면 DTO 재사용, 결측 마커와 실제 데이터의 혼재, 지수 캐시 wiring의 불명확성은 구조를 이해하는 비용을 높이는 요소다. 따라서 이 구조는 단기적으로는 효율적이지만, 장기적으로는 "도메인 모델 / 내부 전송 모델 / API 응답 모델" 경계를 더 분명히 나누는 쪽이 유지보수성에 유리하다.

---

## 참고한 코드 위치

- `src/main/java/depth/finvibe/modules/market/domain/PriceCandle.java`
- `src/main/java/depth/finvibe/modules/market/domain/CurrentPrice.java`
- `src/main/java/depth/finvibe/modules/market/domain/BatchUpdatePrice.java`
- `src/main/java/depth/finvibe/modules/market/domain/enums/Timeframe.java`
- `src/main/java/depth/finvibe/modules/market/dto/PriceCandleDto.java`
- `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java`
- `src/main/java/depth/finvibe/modules/market/application/IndexMinuteCandleCacheService.java`
- `src/main/java/depth/finvibe/modules/market/application/port/in/MarketQueryUseCase.java`
- `src/main/java/depth/finvibe/modules/market/application/port/out/PriceCandleRepository.java`
- `src/main/java/depth/finvibe/modules/market/application/port/out/RealMarketClient.java`
- `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleRepositoryImpl.java`
- `src/main/java/depth/finvibe/modules/market/infra/persistence/PriceCandleJpaRepository.java`
- `src/main/java/depth/finvibe/modules/market/infra/client/RealMarketClientImpl.java`
- `src/main/java/depth/finvibe/modules/market/infra/scheduler/IndexMinuteCandleCacheScheduler.java`
- `src/main/java/depth/finvibe/modules/market/infra/scheduler/InitialMarketDataRunner.java`
- `src/main/java/depth/finvibe/modules/market/api/external/MarketController.java`
