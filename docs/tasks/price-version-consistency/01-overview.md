# 시세 버전 기반 현재가·수익률 정합성 확보

## 연결 Issue

- Issue: [#16 feat: 시세 버전 기반 현재가·수익률 정합성 확보](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/16)
- 관련: [#14 feat: 수익률 Redis-MariaDB fallback 조회 API](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/14) — 조회 API는 이 작업의 S6에서 함께 구현

## 배경과 실제 증상

모놀리식은 KIS 시세 한 건을 두 경로로 보낸다.

- Redis Pub/Sub → 웹소켓 리스너 → 브라우저 (현재가 표시, 종목 행 수익률 계산)
- Kafka `StockPriceUpdatedEvent` → Golang 수익률 워커 → Redis `pf:`/`usr:` 해시 → Batch write-back → MariaDB (랭킹 등)

두 경로는 서로 독립적으로 유실·지연될 수 있다. 그런데 어느 값이 어느 시세로 계산됐는지 식별할 수단이 없다. 그래서 어긋나도 판별할 수 없다.

## 현재 구현과 확인된 사실

1. 경로가 갈라지는 곳은 한 곳이다: `CurrentPriceService.stockPriceUpdated`. Kafka는 가격이 바뀐 틱만 발행한다(`lastPublishedPrices`, 인메모리).
2. KIS 체결시각 `STCK_CNTG_HOUR`는 `HHmmss`, 즉 **초 단위**다. `ts`는 이것을 epoch ms로 바꾼 값이라 하위 3자리가 항상 000이다.
3. 오래된 시세를 복구하는 경로(`StaleCurrentPriceRecoveryService`)는 분봉 `at`으로 같은 `stockPriceUpdated`를 호출한다. 즉 시세 출처가 두 개다.
4. Golang 워커(운영, main `9f43fc3`)는 이미 종목 단위로 단조성을 보장한다. `stock:{id}:price-application`에 `at`과 `price`를 저장하고, 종목 락을 잡은 상태에서 오래된 틱을 버린다.
5. **버그**: 같은 `at`(같은 초)에 가격이 다른 틱이 오면 워커가 `price_timestamp_conflict`로 판단해 **건너뛴다**. 그 종목의 수익률은 다음 틱이 올 때까지 이전 가격 기준으로 남는다. 장 마감 직전의 마지막 틱이라면 다음 장까지 남는다.
6. 워커 결과는 종목별 수익률이 아니라 **포트폴리오·유저 총합**(`pf:{id}`, `usr:{id}`)이고, 클라이언트로 푸시하지 않는다.
7. 클라이언트의 종목 행 수익률은 이미 Pub/Sub 현재가 × 보유 수량으로 클라이언트가 직접 계산한다(`SimulationPortfolioTab`). 그래서 행 안에서는 어긋나지 않는다.
8. 워커 총합을 조회하는 API는 `main`에 아직 없다. #14 `GET /portfolios/valuations`는 [PR #15](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/pull/15)(OPEN)로 구현되어 있다(구현 중 확인, D9).
9. 웹소켓 리스너(`MarketEventBroadcaster`)는 Pub/Sub 페이로드에서 정해진 필드만 골라 복사한다. 새 필드를 추가하면 리스너도 수정해야 한다.

## 해결해야 하는 문제

- P1. 두 경로의 시세에 공통 식별자가 없어 최신 여부와 어긋남을 판별할 수 없다.
- P2. 같은 초 안의 다른 가격 틱을 워커가 버린다(사실 5).
- P3. 워커 총합이 어떤 시세들로 계산됐는지 근거가 남지 않는다. 그래서 클라이언트나 API가 실시간 시세와 대조할 수 없다.
- P4. 수익률 파이프라인이 시세보다 얼마나 뒤처졌는지 관측할 지표가 없다.

## 변경 범위

S1~S6 전체를 이 작업에 포함한다(2026-09-25 사용자 선택).

| 범위 | 내용 | 레포 |
|---|---|---|
| S1 | 단일 지점에서 `priceVersion` 부여, Pub/Sub·Kafka 이벤트에 포함 | monolith |
| S2 | 워커가 `at` 대신 `priceVersion`으로 비교·저장 (P2 해결), 이전 이벤트와 호환 | golang worker |
| S3 | 리스너가 `priceVersion`을 클라이언트에 전달 | websocket-listener |
| S4 | 워커 총합에 종목별 근거 `{stockId: (basisPrice, version)}` 저장 | golang worker |
| S5 | 반영 지연 지표(발행 버전 − 반영 버전) | monolith + golang worker |
| S6 | 조회 API에 근거를 포함하고 클라이언트가 시세 차이만큼 보정, 구독 후 스냅샷 병합 | monolith(#14 선행) + FE |

## 제외 범위

- 수익률을 웹소켓으로 푸시하는 경로 신설 — 수익률 결과로 현재가를 복구하는 방식은 채택하지 않는다. 푸시 경로가 없고, 가격이 바뀐 틱에 대해서만 보유자에게 도달하기 때문이다.
- 현재가 팬아웃을 Kafka로 통일
- Java/Webflux 워커 수정 (운영 무영향)

## 예상 위험과 제약

- 배포 순서: 워커가 `priceVersion` 없는 이벤트도 처리해야 한다. 모놀리식을 먼저 배포하든 워커를 먼저 배포하든 깨지지 않아야 한다.
- 워커 Redis 키 형식 변경 시 Batch warm-up과 호환성을 확인해야 한다.
- 초 안의 수신 순번은 인메모리 카운터다. KIS 구독은 노드별 소유권으로 분산된다(#10). 그래서 모놀리식이 재시작되거나 종목의 구독 소유 노드가 바뀌면 같은 초 안에서 순번이 뒤집힐 수 있다. 영향 범위와 대응을 결정해야 한다.
- 분봉 복구 경로의 `at`이 실시간 틱과 섞일 때 버전 순서를 어떻게 정의할지 결정해야 한다.
- Lua `tonumber`는 double이라 버전은 2^53 이하의 단일 정수로 인코딩해야 한다.
- 여러 레포에 걸친 작업이다. Issue와 작업 문서는 모놀리식에 두고, 다른 레포의 PR은 이 Issue를 참조한다.
