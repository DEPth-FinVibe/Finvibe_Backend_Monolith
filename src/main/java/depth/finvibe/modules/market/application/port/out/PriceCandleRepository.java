package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;

import java.time.LocalDateTime;
import java.util.List;

public interface PriceCandleRepository {

    List<PriceCandle> findExisting(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe);

    List<PriceCandle> findByStockIdAndTimeframeAndAtIn(Long stockId, Timeframe timeframe, List<LocalDateTime> times);

    List<PriceCandle> findLatestByStockIdsAndTimeframe(List<Long> stockIds, Timeframe timeframe);

    /**
     * 지정한 시각(at)의 일봉을 종목별로 조회. 마지막 개장일 종가 조회용.
     */
    List<PriceCandle> findByStockIdsAndTimeframeAndAt(List<Long> stockIds, Timeframe timeframe, LocalDateTime at);

    void saveAll(List<PriceCandle> fetchedResult);

    /**
     * 특정 Stock과 Timeframe으로 데이터 존재 여부 확인
     */
    boolean existsByStockIdAndTimeframe(Long stockId, Timeframe timeframe);
}
