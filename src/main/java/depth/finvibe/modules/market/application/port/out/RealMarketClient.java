package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 한국투자증권, Alplaca 등 외부 API로부터 주식 정보를 조회하는 포트
 */
public interface RealMarketClient {
    List<StockDto.RealMarketStockResponse> fetchStocksInRealMarket();

    List<PriceCandleDto.Response> fetchPriceCandles(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe);

    List<StockDto.RankingResponse> fetchStockRankings();

    List<PriceCandleDto.Response> bulkFetchCurrentPrices(List<String> stockSymbols);
}
