package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.dto.ClosingPriceDto;
import depth.finvibe.modules.market.dto.CurrentPriceDto;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketQueryUseCase {

    List<PriceCandleDto.Response> getStockCandles(
            Long stockId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe
    );

    List<PriceCandleDto.Response> getIndexCandles(
            MarketIndexType indexType,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 여러 종목의 일봉 종가를 한 번에 조회한다. 홈 목록의 미니 차트 전용이며 외부 시세를 호출하지 않는다.
     */
    List<PriceCandleDto.SparklineResponse> getDailySparklines(List<Long> stockIds, int points);

    List<CurrentPriceDto.Response> getCurrentPrices(List<Long> stockIds);

    Long getStockPriceInternal(Long stockId);

    StockDto.Response getStockById(Long stockId);

    List<ClosingPriceDto.Response> getClosingPrices(List<Long> stockIds);

    ClosingPriceDto.BatchResponse getClosingPricesV2(List<Long> stockIds);

    List<StockDto.Response> getTopStocksByValue();

    List<StockDto.Response> getTopStocksByVolume();

    List<StockDto.Response> getTopRisingStocks();

    List<StockDto.Response> getTopFallingStocks();

    /**
     * 종목명 또는 코드 검색
     */
    List<StockDto.Response> searchStocks(String query);

    String getStockNameById(Long stockId);

    Optional<Long> findStockIdBySymbol(String symbol);

    Optional<String> findSymbolByStockId(Long stockId);
}
