-- 종목 캔들 전체 재구성용 운영 SQL
-- 사용자 승인 없이 실행하지 않는다.
-- DB 스냅샷 또는 동등한 복구 수단을 먼저 확보한다.

-- 1. dry-run: 삭제 대상 규모를 기록한다.
select count(*) as total_candles from price_candle;

select timeframe, is_missing, count(*) as candle_count
from price_candle
group by timeframe, is_missing
order by timeframe, is_missing;

select min(at) as oldest_candle_at, max(at) as newest_candle_at
from price_candle;

-- 2. 아래 DELETE는 한 번 실행할 때 최대 10,000행만 삭제한다.
--    각 실행의 소요 시간, DB CPU, 락 대기와 replica 지연을 확인한 뒤 반복한다.
--    DELETE 실행 전 반드시 위 dry-run과 백업 완료를 확인한다.
delete from price_candle
order by id
limit 10000;

select row_count() as deleted_rows;
select count(*) as remaining_candles from price_candle;

-- 3. remaining_candles가 0이 될 때까지 2단계를 수동 반복한다.
-- 4. 애플리케이션 API로 MINUTE, DAY, WEEK를 조회해 KIS REST 재적재를 검증한다.
