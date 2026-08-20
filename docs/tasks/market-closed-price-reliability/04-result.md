# 장외 시세와 휴장일 판정 신뢰성 개선 Result

## 연결 작업

- Monolith Issue: [#7](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/7)
- Batch Issue: [#1](https://github.com/DEPth-FinVibe/Finvibe_Backend_Batch/issues/1)
- 결정: [03-decisions.md](./03-decisions.md)

## 반영 결과

### 휴장일 기반 시장 상태

- 저장된 거래일 달력을 먼저 조회하고 누락 시 KIS 달력을 동기화한다.
- 평일 휴장일은 장중 시간에도 `CLOSED`로 판정한다.
- 달력을 끝내 확인할 수 없으면 `CALENDAR_UNAVAILABLE` 사유와 함께 `CLOSED`로 안전하게 판정한다.
- 기존 `status: OPEN | CLOSED`를 유지하면서 `reason`, `tradingDate`, `asOf`를 추가했다.
- 15:30 이전 장외 조회는 당일이 아닌 직전 완료 거래일을 사용한다.

### 종가 부분 응답 v2

- 기존 `GET /market/stocks/closing-prices` 배열 응답은 변경하지 않았다.
- `GET /market/v2/stocks/closing-prices`를 추가했다.
- v2 응답은 `items`, `missingStockIds`, `partial`, `tradingDate`, `asOf`를 제공한다.
- 기존 endpoint와 v2 모두 누락 종목 수와 부분 응답 횟수를 Micrometer 지표와 경고 로그로 남긴다.
- KIS 멀티종목 배치 실패 횟수를 별도 지표로 남긴다.

### 종가 보존

- 장 시작 시 종가 전체 삭제 동작을 제거했다.
- 저장소에 거래일 기준 삭제 연산을 추가했고 Batch가 달력 기준 30일 이전 데이터만 정리한다.

## 주요 지표

- `market.calendar.sync.failures`
- `market.calendar.status.unknown`
- `market.closing.price.partial.responses{endpoint=legacy|v2}`
- `market.closing.price.missing.stocks{endpoint=legacy|v2}`
- `market.kis.bulk.price.batch.failures`

Batch 워밍업 지표는 Batch 저장소 Result에 기록한다.

## 검증

- `./gradlew compileJava` 성공
- `./gradlew test` 성공
- 추가 테스트:
  - 정상 거래일 장중, 평일 휴장일, 주말, 달력 확인 실패 판정
  - 장 마감 전 최근 완료 거래일 계산
  - v2 종가 일부 누락 응답 및 지표
- `git diff --check` 성공

## 운영 적용 및 롤백

- DB 스키마 변경은 없다.
- 기존 프론트 endpoint와 응답 배열은 유지되므로 프론트 동시 배포가 필요하지 않다.
- 사용자 콜드 요청을 줄이려면 Batch 변경을 먼저 배포한 뒤 Monolith를 배포하는 순서를 권장한다.
- Monolith 롤백 시 신규 v2 endpoint와 상태 메타데이터만 사라지며, 보존된 종가 데이터는 기존 코드에서도 읽을 수 있다.
- 무중단 배포 및 Kubernetes 설정은 요청에 따라 변경하지 않았다.
