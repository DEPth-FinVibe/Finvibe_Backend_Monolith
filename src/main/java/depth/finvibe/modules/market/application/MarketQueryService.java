package depth.finvibe.modules.market.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import depth.finvibe.modules.market.application.port.out.CandleFetchResult;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.ClosingPriceRepository;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.StockRankingRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.ClosingPrice;
import depth.finvibe.modules.market.domain.CurrentPrice;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.StockRanking;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.domain.enums.RankType;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.ClosingPriceDto;
import depth.finvibe.modules.market.dto.CurrentPriceDto;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.common.investment.lock.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketQueryService implements MarketQueryUseCase {

    private final PriceCandleRepository priceCandleRepository;
    private final RealMarketClient realMarketClient;
    private final CurrentPriceRepository currentPriceRepository;
    private final ClosingPriceRepository closingPriceRepository;
    private final CurrentStockWatcherRepository currentStockWatcherRepository;
    private final StockRankingRepository stockRankingRepository;
    private final StockRepository stockRepository;
    private final DistributedLockManager distributedLockManager;
    private final HolidayCalendarService holidayCalendarService;
    private final MarketStatusService marketStatusService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<Long, String> stockSymbolCache = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> currentPriceMissLocks = new ConcurrentHashMap<>();
    @Value("${market.provider:kis}")
    private String marketProvider;

    @Override
    public List<PriceCandleDto.Response> getStockCandles(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
        String lockKey = String.format("stock:candle:%d:%s", stockId, timeframe);
        try {
            return distributedLockManager.executeWithLock(
                    lockKey,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(60),
                    () -> fetchStockCandlesWithLock(stockId, startTime, endTime, timeframe)
            );
        } catch (LockAcquisitionException ex) {
            return fallbackToCachedCandles(stockId, startTime, endTime, timeframe, "lock", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceCandleDto.Response> getIndexCandles(
            MarketIndexType indexType,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        LocalDateTime normalizedStart = Timeframe.MINUTE.normalizeStart(startTime);
        LocalDateTime normalizedEnd = Timeframe.MINUTE.normalizeStart(endTime);

        Stock indexStock = stockRepository.findBySymbol(indexType.getSymbol())
                .orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));

        return priceCandleRepository.findExisting(indexStock.getId(), normalizedStart, normalizedEnd, Timeframe.MINUTE)
                .stream()
                .filter(candle -> !candle.getIsMissing())
                .map(PriceCandleDto.Response::from)
                .toList();
    }

    private List<PriceCandleDto.Response> fetchStockCandlesWithLock(
            Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
        LocalDateTime normalizedStart = timeframe.normalizeStart(startTime);
        LocalDateTime normalizedEnd = timeframe.normalizeEnd(endTime);
        List<PriceCandle> existingCandles = findExistingCandles(
                stockId,
                normalizedStart,
                normalizedEnd,
                timeframe
        );
        List<LocalDateTime> missingCandleTimes = calculateMissingCandleTimes(
                normalizedStart,
                normalizedEnd,
                timeframe,
                existingCandles
        );

        if (missingCandleTimes.isEmpty()) {
            return toActualCandleDtos(existingCandles);
        }

        LocalDateTime earliestTime = missingCandleTimes.getFirst();
        LocalDateTime latestTime = missingCandleTimes.getLast();
        CandleFetchResult fetchResult = realMarketClient.fetchPriceCandles(
                stockId,
                earliestTime,
                latestTime,
                timeframe
        );

        if (fetchResult.isFailed() || fetchResult.candles().isEmpty()) {
            recordCandleProviderFailure(stockId, timeframe, earliestTime, latestTime);
            return fallbackToExistingCandles(existingCandles, stockId, timeframe, "provider");
        }

        if (fetchResult.isPartial()) {
            meterRegistry.counter("market.candle.provider.partial", "timeframe", timeframe.name()).increment();
            log.warn(
                    "Candle provider returned partial data. stockId={}, timeframe={}, startTime={}, endTime={}, count={}",
                    stockId,
                    timeframe,
                    earliestTime,
                    latestTime,
                    fetchResult.candles().size()
            );
        }

        List<PriceCandleDto.Response> fetchedCandles = fetchResult.candles();
        transactionTemplate.executeWithoutResult(status -> saveFetchedCandles(fetchedCandles, existingCandles));
        return mergeAndSortCandles(existingCandles, fetchedCandles);
    }

    private List<PriceCandle> findExistingCandles(
            Long stockId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe
    ) {
        List<PriceCandle> candles = transactionTemplate.execute(status ->
                priceCandleRepository.findExisting(stockId, startTime, endTime, timeframe));
        return candles == null ? List.of() : candles;
    }

    private List<PriceCandleDto.Response> fallbackToCachedCandles(
            Long stockId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe,
            String reason,
            RuntimeException cause
    ) {
        List<PriceCandle> existingCandles = findExistingCandles(
                stockId,
                timeframe.normalizeStart(startTime),
                timeframe.normalizeEnd(endTime),
                timeframe
        );
        log.warn(
                "Candle query fallback after {} failure. stockId={}, timeframe={}, startTime={}, endTime={}",
                reason,
                stockId,
                timeframe,
                startTime,
                endTime,
                cause
        );
        return fallbackToExistingCandles(existingCandles, stockId, timeframe, reason);
    }

    private List<PriceCandleDto.Response> fallbackToExistingCandles(
            List<PriceCandle> existingCandles,
            Long stockId,
            Timeframe timeframe,
            String reason
    ) {
        List<PriceCandleDto.Response> cachedCandles = toActualCandleDtos(existingCandles);
        if (!cachedCandles.isEmpty()) {
            meterRegistry.counter(
                    "market.candle.cache.fallback",
                    "timeframe",
                    timeframe.name(),
                    "reason",
                    reason
            ).increment();
            return cachedCandles;
        }

        meterRegistry.counter(
                "market.candle.unavailable",
                "timeframe",
                timeframe.name(),
                "reason",
                reason
        ).increment();
        log.warn("Candle data is temporarily unavailable. stockId={}, timeframe={}, reason={}",
                stockId, timeframe, reason);
        throw new DomainException(MarketErrorCode.CANDLE_TEMPORARILY_UNAVAILABLE);
    }

    private void recordCandleProviderFailure(
            Long stockId,
            Timeframe timeframe,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        meterRegistry.counter("market.candle.provider.failures", "timeframe", timeframe.name()).increment();
        log.warn(
                "Candle provider failed or returned no usable data. stockId={}, timeframe={}, startTime={}, endTime={}",
                stockId,
                timeframe,
                startTime,
                endTime
        );
    }

    private List<PriceCandleDto.Response> toActualCandleDtos(List<PriceCandle> candles) {
        return candles.stream()
                .filter(candle -> !candle.getIsMissing())
                .map(PriceCandleDto.Response::from)
                .toList();
    }

    private List<PriceCandleDto.Response> mergeAndSortCandles(
            List<PriceCandle> existingCandles,
            List<PriceCandleDto.Response> fetchedCandles
    ) {
        List<PriceCandleDto.Response> existingCandleDtos = toActualCandleDtos(existingCandles);

        Set<LocalDateTime> existingTimes = existingCandleDtos.stream()
                .map(PriceCandleDto.Response::getAt)
                .collect(Collectors.toSet());

        List<PriceCandleDto.Response> uniqueFetchedCandles = fetchedCandles.stream()
                .filter(candle -> !existingTimes.contains(candle.getAt()))
                .toList();

        return Stream.concat(existingCandleDtos.stream(), uniqueFetchedCandles.stream())
                .sorted(Comparator.comparing(PriceCandleDto.Response::getAt))
                .toList();
    }

    private void saveFetchedCandles(
            List<PriceCandleDto.Response> fetchedCandles,
            List<PriceCandle> existingCandles
    ) {
        Map<LocalDateTime, PriceCandle> existingByTime = existingCandles.stream()
                .collect(Collectors.toMap(PriceCandle::getAt, candle -> candle));
        List<PriceCandle> changedCandles = new ArrayList<>();

        for (PriceCandleDto.Response fetchedCandle : fetchedCandles) {
            PriceCandle existing = existingByTime.get(fetchedCandle.getAt());
            if (existing == null) {
                changedCandles.add(createPriceCandleFrom(fetchedCandle));
                continue;
            }
            if (existing.getIsMissing()) {
                existing.restore(
                        fetchedCandle.getOpen(),
                        fetchedCandle.getHigh(),
                        fetchedCandle.getLow(),
                        fetchedCandle.getClose(),
                        fetchedCandle.getPrevDayChangePct(),
                        fetchedCandle.getVolume(),
                        fetchedCandle.getValue()
                );
                changedCandles.add(existing);
            }
        }

        if (!changedCandles.isEmpty()) {
            priceCandleRepository.saveAll(changedCandles);
        }
    }

    private PriceCandle createPriceCandleFrom(PriceCandleDto.Response dto) {
        return PriceCandle.builder()
                .stockId(dto.getStockId())
                .timeframe(dto.getTimeframe())
                .at(dto.getAt())
                .isMissing(false)
                .open(dto.getOpen())
                .close(dto.getClose())
                .high(dto.getHigh())
                .low(dto.getLow())
                .volume(dto.getVolume())
                .value(dto.getValue())
                .prevDayChangePct(dto.getPrevDayChangePct())
                .build();
    }

    private List<LocalDateTime> calculateMissingCandleTimes(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe,
            List<PriceCandle> existingCandles
    ) {
        List<LocalDateTime> shouldHaveCandleTimes = generateCandleTimesInRange(startTime, endTime, timeframe);

        Set<LocalDateTime> existingCandleTimes = existingCandles.stream()
                .filter(candle -> !candle.getIsMissing())
                .map(PriceCandle::getAt)
                .collect(Collectors.toSet());

        return shouldHaveCandleTimes.stream()
                .filter(time -> !existingCandleTimes.contains(time))
                .toList();
    }

    private List<LocalDateTime> generateCandleTimesInRange(LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
        return switch (timeframe) {
            case MINUTE -> generateMinuteCandleTimes(startTime, endTime);
            case DAY -> generateDailyCandleTimes(startTime, endTime);
            case WEEK, MONTH, YEAR -> generatePeriodCandleTimes(startTime, endTime, timeframe);
        };
    }

    private List<LocalDateTime> generateMinuteCandleTimes(LocalDateTime startTime, LocalDateTime endTime) {
        List<LocalDateTime> candleTimes = new ArrayList<>();
        LocalDate currentDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();

        while (!currentDate.isAfter(endDate)) {
            if (shouldQueryTradingDate(currentDate)) {
                LocalDateTime sessionStart = MarketHours.sessionStart(currentDate);
                LocalDateTime sessionEnd = MarketHours.sessionEnd(currentDate);
                LocalDateTime current = sessionStart.isAfter(startTime) ? sessionStart : Timeframe.MINUTE.normalizeStart(startTime);
                LocalDateTime effectiveEnd = sessionEnd.isBefore(endTime) ? sessionEnd : Timeframe.MINUTE.normalizeStart(endTime);

                while (!current.isAfter(effectiveEnd)) {
                    candleTimes.add(current);
                    current = current.plusMinutes(1);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return candleTimes;
    }

    private List<LocalDateTime> generateDailyCandleTimes(LocalDateTime startTime, LocalDateTime endTime) {
        List<LocalDateTime> candleTimes = new ArrayList<>();
        LocalDate currentDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();

        while (!currentDate.isAfter(endDate)) {
            if (shouldQueryTradingDate(currentDate)) {
                candleTimes.add(currentDate.atStartOfDay());
            }
            currentDate = currentDate.plusDays(1);
        }
        return candleTimes;
    }

    private boolean shouldQueryTradingDate(LocalDate date) {
        if (!MarketHours.isWeekday(date)) {
            return false;
        }

        TradingDayStatus status = holidayCalendarService.getTradingDayStatus(date);
        if (status == TradingDayStatus.CLOSED) {
            return false;
        }
        if (status == TradingDayStatus.UNKNOWN) {
            meterRegistry.counter("market.candle.calendar.unknown").increment();
            log.warn("Use weekday fallback for candle query because calendar status is unknown. date={}", date);
        }
        return true;
    }

    private List<LocalDateTime> generatePeriodCandleTimes(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe
    ) {
        List<LocalDateTime> candleTimes = new ArrayList<>();
        LocalDateTime current = timeframe.normalizeStart(startTime);

        while (!current.isAfter(endTime)) {
            candleTimes.add(current);
            current = timeframe.nextTime(current);
        }
        return candleTimes;
    }


    /***
     * 여러 종목의 현재가를 조회
     * 종목이 인덱스에는 들어있지만 아직 현재가가 캐싱되지 않은 경우 예외 발생됨. Infra에서 시간을 두고 N번 재시도.
     * @param stockIds 조회할 종목 ID 리스트 (현재가 캐시에 존재하는 종목이어야 함)
     * @return 현재가 DTO 리스트
     */
    @Override
    public List<CurrentPriceDto.Response> getCurrentPrices(List<Long> stockIds) {
        if(!currentStockWatcherRepository.allExistsByStockIds(stockIds)) {
            throw new DomainException(MarketErrorCode.STOCK_NOT_FOUND);
        }

        // db에서 종목 정보도 함께 조회
        List<Stock> stocks = stockRepository.findAllById(stockIds);

        //인덱스에는 들어왔지만 실제로 값이 들어오지 않은 경우 예외가 발생. Infra에서 시간을 두고 N번 재시도.
        List<CurrentPrice> prices = currentPriceRepository.findByStockIds(stockIds);

        // 종목Id -> 종목 매핑
        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getId, stock -> stock));

        if (stockMap.size() != new HashSet<>(stockIds).size()) {
            Set<Long> missingStockIds = new HashSet<>(stockIds);
            missingStockIds.removeAll(stockMap.keySet());
            log.warn("Some stockIds are missing in DB. stockIds={}", missingStockIds);
        }

        // 반환
        return prices.stream()
                .filter(price -> stockMap.containsKey(price.getStockId()))
                .map(price -> CurrentPriceDto.Response.of(price, stockMap.get(price.getStockId())))
                .toList();
    }

    @Override
    @Transactional
    public Long getStockPriceInternal(Long stockId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "unknown";

        try {
            if (marketStatusService.getMarketStatus().getStatus() == MarketStatus.CLOSED && !isMockProvider()) {
                List<ClosingPriceDto.Response> closingPrices = getClosingPrices(List.of(stockId));
                if (!closingPrices.isEmpty()) {
                    result = "market_closed";
                    return closingPrices.getFirst().getClose().longValue();
                }
                result = "market_closed_empty";
                throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
            }

            Optional<Long> cachedPrice = findCachedCurrentPrice(stockId);
            if (cachedPrice.isPresent()) {
                result = "hit";
                return cachedPrice.get();
            }

            Long refreshedPrice = refreshCurrentPriceOnMiss(stockId);
            result = "miss";
            return refreshedPrice;
        } finally {
            meterRegistry.counter("market.current_price.cache.requests", "result", result).increment();
            sample.stop(
                    Timer.builder("market.current_price.cache.read.duration")
                            .tag("result", result)
                            .register(meterRegistry)
            );
        }
    }

    private boolean isMockProvider() {
        return "mock".equalsIgnoreCase(marketProvider);
    }

    private Optional<Long> findCachedCurrentPrice(Long stockId) {
        List<CurrentPrice> currentPrices = currentPriceRepository.findByStockIds(List.of(stockId));
        if (currentPrices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(currentPrices.getFirst().getClose().longValue());
    }

    private Long refreshCurrentPriceOnMiss(Long stockId) {
        ReentrantLock lock = currentPriceMissLocks.computeIfAbsent(stockId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Optional<Long> cachedPrice = findCachedCurrentPrice(stockId);
            if (cachedPrice.isPresent()) {
                return cachedPrice.get();
            }

            String symbol = stockSymbolCache.computeIfAbsent(stockId, this::resolveStockSymbol);
            List<PriceCandleDto.Response> snapshots = realMarketClient.bulkFetchCurrentPrices(List.of(symbol));
            if (snapshots.isEmpty()) {
                throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
            }

            PriceCandleDto.Response snapshot = snapshots.getFirst();
            CurrentPrice currentPrice = new CurrentPrice(
                    stockId,
                    snapshot.getAt(),
                    snapshot.getClose(),
                    snapshot.getOpen(),
                    snapshot.getHigh(),
                    snapshot.getLow(),
                    snapshot.getClose(),
                    snapshot.getPrevDayChangePct(),
                    snapshot.getVolume(),
                    snapshot.getValue()
            );
            currentPriceRepository.upsertCurrentPrice(currentPrice);
            return snapshot.getClose().longValue();
        } finally {
            lock.unlock();
        }
    }

    private String resolveStockSymbol(Long stockId) {
        return stockRepository.findById(stockId)
                .map(Stock::getSymbol)
                .orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));
    }

    @Override
    public StockDto.Response getStockById(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));

        return StockDto.Response.from(stock);
    }

    @Override
    @Transactional
    public List<ClosingPriceDto.Response> getClosingPrices(List<Long> stockIds) {
        ClosingPriceQueryResult result = queryClosingPrices(stockIds);
        recordClosingPriceResult("legacy", result);
        return result.items();
    }

    @Override
    @Transactional
    public ClosingPriceDto.BatchResponse getClosingPricesV2(List<Long> stockIds) {
        ClosingPriceQueryResult result = queryClosingPrices(stockIds);
        recordClosingPriceResult("v2", result);
        return ClosingPriceDto.BatchResponse.of(
                result.items(),
                result.missingStockIds(),
                result.tradingDate(),
                result.asOf()
        );
    }

    private ClosingPriceQueryResult queryClosingPrices(List<Long> stockIds) {
        LocalDateTime asOf = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (stockIds == null || stockIds.isEmpty()) {
            return new ClosingPriceQueryResult(List.of(), List.of(), null, asOf);
        }

        if (marketStatusService.getMarketStatus().getStatus() == MarketStatus.OPEN) {
            throw new DomainException(MarketErrorCode.CLOSING_PRICE_NOT_AVAILABLE_DURING_MARKET_OPEN);
        }

        List<Long> requestedStockIds = stockIds.stream()
                .distinct()
                .toList();

        List<Stock> stocks = stockRepository.findAllById(requestedStockIds);
        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getId, stock -> stock));

        if (stockMap.size() != requestedStockIds.size()) {
            Set<Long> missingStockIds = new HashSet<>(requestedStockIds);
            missingStockIds.removeAll(stockMap.keySet());
            log.warn("Some stockIds are missing in DB. stockIds={}", missingStockIds);
        }

        LocalDate lastTradingDay = holidayCalendarService.getLastCompletedTradingDay(asOf)
                .orElseThrow(() -> new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE));
        LocalDateTime closingAt = lastTradingDay.atTime(LocalTime.of(15, 30));

        List<ClosingPrice> cachedClosingPrices = closingPriceRepository
                .findByStockIdsAndTradingDate(requestedStockIds, lastTradingDay);
        Map<Long, ClosingPrice> closingPriceByStockId = cachedClosingPrices.stream()
                .collect(Collectors.toMap(ClosingPrice::getStockId, closingPrice -> closingPrice));

        List<Long> missingStockIds = requestedStockIds.stream()
                .filter(stockMap::containsKey)
                .filter(stockId -> !closingPriceByStockId.containsKey(stockId))
                .toList();

        if (!missingStockIds.isEmpty()) {
            List<String> missingSymbols = missingStockIds.stream()
                    .map(stockMap::get)
                    .filter(Objects::nonNull)
                    .map(Stock::getSymbol)
                    .filter(Objects::nonNull)
                    .filter(symbol -> !symbol.isBlank())
                    .toList();

            if (!missingSymbols.isEmpty()) {
                List<PriceCandleDto.Response> snapshots = realMarketClient.bulkFetchCurrentPrices(missingSymbols);
                Set<Long> missingStockIdSet = new HashSet<>(missingStockIds);

                List<ClosingPrice> fetchedClosingPrices = snapshots.stream()
                        .filter(snapshot -> missingStockIdSet.contains(snapshot.getStockId()))
                        .map(snapshot -> ClosingPrice.create(
                                snapshot.getStockId(),
                                lastTradingDay,
                                closingAt,
                                snapshot.getClose(),
                                snapshot.getPrevDayChangePct(),
                                snapshot.getVolume(),
                                snapshot.getValue()
                        ))
                        .toList();

                closingPriceRepository.saveAll(fetchedClosingPrices);
                fetchedClosingPrices.forEach(price -> closingPriceByStockId.put(price.getStockId(), price));
            }
        }

        List<ClosingPriceDto.Response> items = requestedStockIds.stream()
                .filter(stockMap::containsKey)
                .filter(closingPriceByStockId::containsKey)
                .map(stockId -> ClosingPriceDto.Response.from(closingPriceByStockId.get(stockId), stockMap.get(stockId)))
                .toList();

        Set<Long> returnedStockIds = items.stream()
                .map(ClosingPriceDto.Response::getStockId)
                .collect(Collectors.toSet());
        List<Long> unresolvedStockIds = requestedStockIds.stream()
                .filter(stockId -> !returnedStockIds.contains(stockId))
                .toList();

        return new ClosingPriceQueryResult(items, unresolvedStockIds, lastTradingDay, asOf);
    }

    private void recordClosingPriceResult(String endpoint, ClosingPriceQueryResult result) {
        if (result.missingStockIds().isEmpty()) {
            return;
        }
        meterRegistry.counter("market.closing.price.partial.responses", "endpoint", endpoint).increment();
        meterRegistry.counter("market.closing.price.missing.stocks", "endpoint", endpoint)
                .increment(result.missingStockIds().size());
        log.warn(
                "Closing price response is partial. endpoint={}, tradingDate={}, missingCount={}, missingStockIds={}",
                endpoint,
                result.tradingDate(),
                result.missingStockIds().size(),
                result.missingStockIds()
        );
    }

    private record ClosingPriceQueryResult(
            List<ClosingPriceDto.Response> items,
            List<Long> missingStockIds,
            LocalDate tradingDate,
            LocalDateTime asOf
    ) {
    }

    private void fetchLatestDailyCandles(List<Long> stockIds) {
        LocalDateTime endTime = Timeframe.DAY.lastCompletedTime(LocalDateTime.now());
        LocalDateTime startTime = endTime.minusDays(30);

        for (Long stockId : stockIds) {
            String lockKey = String.format("stock:candle:%d:%s", stockId, Timeframe.DAY);
            distributedLockManager.executeWithLock(
                    lockKey,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(60),
                    () -> fetchStockCandlesWithLock(stockId, startTime, endTime, Timeframe.DAY)
            );
        }
    }

    @Override
    public List<StockDto.Response> getTopStocksByValue() {
        return getTopStocksByRankType(RankType.TOP_VALUE);
    }

    @Override
    public List<StockDto.Response> getTopStocksByVolume() {
        return getTopStocksByRankType(RankType.TOP_VOLUME);
    }

    @Override
    public List<StockDto.Response> getTopRisingStocks() {
        return getTopStocksByRankType(RankType.TOP_RISING);
    }

    @Override
    public List<StockDto.Response> getTopFallingStocks() {
        return getTopStocksByRankType(RankType.TOP_FALLING);
    }

    @Override
    public List<StockDto.Response> searchStocks(String query) {
        String trimmedQuery = query == null ? "" : query.trim();

        if (trimmedQuery.isEmpty()) {
            return List.of();
        }

        List<Stock> stocks = stockRepository.searchByNameOrSymbol(trimmedQuery);

        return stocks.stream()
                .map(StockDto.Response::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getStockNameById(Long stockId) {
        return stockRepository.findById(stockId)
                .map(Stock::getName)
                .orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findStockIdBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .map(Stock::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findSymbolByStockId(Long stockId) {
        return stockRepository.findById(stockId)
                .map(Stock::getSymbol);
    }

    private List<StockDto.Response> getTopStocksByRankType(RankType rankType) {
        List<StockRanking> rankings = stockRankingRepository.findByRankType(rankType);
        
        List<Long> stockIds = rankings.stream()
                .map(StockRanking::getStockId)
                .toList();
        
        List<Stock> stocks = stockRepository.findAllById(stockIds);
        
        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getId, stock -> stock));
        
        return rankings.stream()
                .map(ranking -> stockMap.get(ranking.getStockId()))
                .filter(Objects::nonNull)
                .map(StockDto.Response::from)
                .toList();
    }
}
