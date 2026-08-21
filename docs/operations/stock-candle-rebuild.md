# 종목 캔들 전체 재구성 운영 절차

## 목적

`market.provider=mock` 운영 중 `price_candle`에 저장된 분봉·일봉·주봉 오염 데이터를 제거하고 KIS REST 실데이터로 다시 구성한다.

## 중요한 제약

- 이 절차는 `price_candle`의 모든 행을 삭제한다.
- KIS가 제공하지 않는 오래된 분봉은 복구되지 않을 수 있다.
- 별도 Batch 재적재는 수행하지 않는다. 삭제 후 차트 API 요청과 WebSocket 실시간 집계로 데이터가 다시 저장된다.
- 애플리케이션 배포나 시작 과정에서 자동 실행하지 않는다.
- `docs/operations/stock-candle-rebuild.sql`의 DELETE는 별도 사용자 승인 이후에만 실행한다.

## 실행 전 확인

1. 수정 버전이 배포되어 `market.provider=kis`로 실행 중인지 확인한다.
2. DB 스냅샷 또는 `price_candle`을 복원할 수 있는 백업을 확보한다.
3. SQL의 dry-run 세 쿼리를 실행해 전체 건수, timeframe별 건수와 시간 범위를 기록한다.
4. KIS REST 자격 증명과 캔들 API가 정상인지 삭제 전에 종목 4971의 DAY 조회로 확인한다.
5. DB CPU, 락 대기와 replica 지연을 관찰할 수 있는 상태에서 시작한다.

## 분할 삭제

1. SQL의 `delete ... limit 10000`을 한 번 실행한다.
2. `deleted_rows`와 `remaining_candles`를 기록한다.
3. DB 부하가 안정적일 때만 다음 10,000행을 삭제한다.
4. 오류나 과도한 락 대기가 발생하면 즉시 반복 실행을 중단한다.
5. `remaining_candles=0`을 확인한다.

## 재적재 검증

1. 종목 4971의 MINUTE 조회를 실행하고 HTTP 200, 비어 있지 않은 배열, KST 장 시간과 OHLCV를 확인한다.
2. 같은 종목의 DAY와 WEEK 조회를 실행해 KIS 실데이터가 저장되는지 확인한다.
3. `price_candle`을 timeframe별로 다시 집계해 행이 증가하는지 확인한다.
4. 장중에는 WebSocket 구독 종목의 현재 분봉이 약 5초 이내에 저장되는지 확인한다.
5. 다음 분 경계 이후 직전 분봉을 조회해 KIS REST 결과로 최종 보정되는지 확인한다.

## 중단 및 복구

- 삭제 도중 중단해도 남은 행은 유지된다. 원인을 해결한 뒤 남은 건수부터 재개한다.
- 삭제한 데이터 자체는 SQL rollback으로 복구되지 않는다. 문제가 생기면 실행 전 확보한 백업을 사용한다.
- KIS 재적재가 실패하면 추가 삭제를 중단하고 캔들 API 로그와 `market.candle.provider.failures`를 확인한다.
