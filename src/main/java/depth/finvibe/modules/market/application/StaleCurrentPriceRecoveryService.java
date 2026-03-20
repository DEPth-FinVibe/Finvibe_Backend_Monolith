package depth.finvibe.modules.market.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleCurrentPriceRecoveryService {

  private final CurrentStockWatcherRepository currentStockWatcherRepository;
  private final CurrentPriceRepository currentPriceRepository;
  private final StockRepository stockRepository;
  private final RealMarketClient realMarketClient;
  private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
  private final Map<Long, String> stockSymbolCache = new ConcurrentHashMap<>();

  public void recoverStaleCurrentPrices(Duration staleThreshold) {
    List<Long> activeStockIds = currentStockWatcherRepository.findActiveStockIds();
    if (activeStockIds.isEmpty()) {
      return;
    }

    LocalDateTime thresholdAt = LocalDateTime.now().minus(staleThreshold);
    Map<Long, LocalDateTime> lastUpdatedAtMap = currentPriceRepository.findLastUpdatedAtByStockIds(activeStockIds);

    List<Long> staleStockIds = activeStockIds.stream()
            .filter(stockId -> isStale(lastUpdatedAtMap.get(stockId), thresholdAt))
            .toList();
    if (staleStockIds.isEmpty()) {
      return;
    }

    Map<Long, String> symbolByStockId = resolveSymbolsByStockIds(staleStockIds);

    List<String> symbols = staleStockIds.stream()
            .map(symbolByStockId::get)
            .filter(symbol -> symbol != null && !symbol.isBlank())
            .toList();
    if (symbols.isEmpty()) {
      return;
    }

    List<PriceCandleDto.Response> candles = realMarketClient.bulkFetchCurrentPrices(symbols);
    if (candles.isEmpty()) {
      log.debug("Stale current price recovery returned no data. staleCount={}", staleStockIds.size());
      return;
    }

    List<CurrentPriceUpdatedEvent> events = candles.stream()
            .map(this::toEvent)
            .toList();

    for (CurrentPriceUpdatedEvent event : events) {
      currentPriceCommandUseCase.stockPriceUpdated(event);
    }

    log.debug("Recovered stale current prices. staleCount={}, recoveredCount={}", staleStockIds.size(), events.size());
  }

  private boolean isStale(LocalDateTime lastUpdatedAt, LocalDateTime thresholdAt) {
    return lastUpdatedAt == null || lastUpdatedAt.isBefore(thresholdAt);
  }

  private CurrentPriceUpdatedEvent toEvent(PriceCandleDto.Response candle) {
    return CurrentPriceUpdatedEvent.builder()
            .stockId(candle.getStockId())
            .at(candle.getAt())
            .open(candle.getOpen())
            .high(candle.getHigh())
            .low(candle.getLow())
            .close(candle.getClose())
            .prevDayChangePct(candle.getPrevDayChangePct())
            .volume(candle.getVolume())
            .value(candle.getValue())
            .build();
  }

  private Map<Long, String> resolveSymbolsByStockIds(List<Long> stockIds) {
    if (stockIds.isEmpty()) {
      return Map.of();
    }

    LinkedHashSet<Long> uniqueStockIds = new LinkedHashSet<>(stockIds);
    List<Long> missingStockIds = uniqueStockIds.stream()
            .filter(stockId -> !stockSymbolCache.containsKey(stockId))
            .toList();

    if (!missingStockIds.isEmpty()) {
      List<Stock> stocks = stockRepository.findAllById(missingStockIds);
      for (Stock stock : stocks) {
        stockSymbolCache.put(stock.getId(), stock.getSymbol());
      }
    }

    Map<Long, String> symbolByStockId = new HashMap<>(uniqueStockIds.size());
    for (Long stockId : uniqueStockIds) {
      String symbol = stockSymbolCache.get(stockId);
      if (symbol != null && !symbol.isBlank()) {
        symbolByStockId.put(stockId, symbol);
      }
    }
    return symbolByStockId;
  }
}
