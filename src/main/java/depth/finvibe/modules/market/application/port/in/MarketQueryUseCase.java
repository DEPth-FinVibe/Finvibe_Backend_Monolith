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

    List<CurrentPriceDto.Response> getCurrentPrices(List<Long> stockIds);

    Long getStockPriceInternal(Long stockId);

    StockDto.Response getStockById(Long stockId);

    List<ClosingPriceDto.Response> getClosingPrices(List<Long> stockIds);

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
