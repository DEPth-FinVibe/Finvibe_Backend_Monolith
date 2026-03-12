package depth.finvibe.modules.market.infra.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.RankType;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto.RankingResponse;
import depth.finvibe.modules.market.dto.StockDto.RealMarketStockResponse;
import depth.finvibe.modules.market.infra.client.dto.KisDto;
import depth.finvibe.common.error.DomainException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealMarketClientImpl implements RealMarketClient {

    private static final int DAILY_CHART_BATCH_LIMIT = 100;
    private static final int DAILY_CHART_MAX_CALL_COUNT = 50;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CHANGE_RATE_SCALE = 6;

    private final KisApiClient kisApiClient;
    private final List<KisFileClient> kisFileClient;
    private final StockRepository stockRepository;

    @Override
    public List<PriceCandleDto.Response> fetchPriceCandles(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
        if (startTime == null || endTime == null) {
            return List.of();
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));

        return switch (timeframe) {
            case MINUTE -> fetchIntradayCandles(stock.getSymbol(), stockId, startTime, endTime);
            case DAY, WEEK, MONTH, YEAR -> fetchDailyCandles(stock.getSymbol(), stockId, startTime, endTime, timeframe);
        };
    }

    @Override
    public List<RealMarketStockResponse> fetchStocksInRealMarket() {
        List<CompletableFuture<List<RealMarketStockResponse>>> futures = kisFileClient.stream()
                .map(client -> CompletableFuture.supplyAsync(client::fetchStocksInKisFile))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<RankingResponse> fetchStockRankings() {
        List<KisDto.ConditionalStockSearchResponseItem> valueTop100 = kisApiClient.fetchConditionalStockSearch(KisApiClient.ConditionSeq.TRADE_VALUE); // 거래대금 상위 100종목
        List<KisDto.ConditionalStockSearchResponseItem> volumeTop100 = kisApiClient.fetchConditionalStockSearch(KisApiClient.ConditionSeq.VOLUME); // 거래량 상위 100종목
        List<KisDto.ConditionalStockSearchResponseItem> risingTop100 = kisApiClient.fetchConditionalStockSearch(KisApiClient.ConditionSeq.RISE_RATE); // 상승률 상위 100종목
        List<KisDto.ConditionalStockSearchResponseItem> fallingTop100 = kisApiClient.fetchConditionalStockSearch(KisApiClient.ConditionSeq.FALL_RATE); // 하락률 상위 100종목

        List<RankingResponse> result = new ArrayList<>();

        addRankingResponses(result, valueTop100, RankType.TOP_VALUE);
        addRankingResponses(result, volumeTop100, RankType.TOP_VOLUME);
        addRankingResponses(result, risingTop100, RankType.TOP_RISING);
        addRankingResponses(result, fallingTop100, RankType.TOP_FALLING);

        return result;
    }

    private void addRankingResponses(List<RankingResponse> result,
                                   List<KisDto.ConditionalStockSearchResponseItem> items,
                                   RankType rankType) {
        for (int i = 0; i < items.size(); i++) {
            result.add(RankingResponse.builder()
                .symbol(items.get(i).getCode())
                .rankType(rankType)
                .rank(i + 1)
                .build());
        }
    }

    private List<PriceCandleDto.Response> fetchIntradayCandles(
            String symbol,
            Long stockId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        LocalDateTime normalizedStart = startTime.withSecond(0).withNano(0);
        LocalDateTime normalizedEnd = endTime.withSecond(0).withNano(0);

        if (normalizedStart.isAfter(normalizedEnd)) {
            return List.of();
        }

        Map<LocalDateTime, PriceCandleDto.Response> minuteCandles = new HashMap<>();
        List<LocalDate> tradingDates = getTradingDates(normalizedStart.toLocalDate(), normalizedEnd.toLocalDate());
        if (tradingDates.isEmpty()) {
            return List.of();
        }

        for (LocalDate tradingDate : tradingDates) {
            LocalDateTime sessionStart = tradingDate.atTime(LocalTime.of(9, 0));
            LocalDateTime sessionEnd = tradingDate.atTime(LocalTime.of(15, 30));
            LocalDateTime effectiveStart = clampToSessionStart(normalizedStart, sessionStart, sessionEnd);
            LocalDateTime effectiveEnd = clampToSessionEnd(normalizedEnd, sessionStart, sessionEnd);

            if (effectiveStart == null || effectiveEnd == null || effectiveStart.isAfter(effectiveEnd)) {
                continue;
            }

            List<LocalDateTime> queryTimes = buildQueryTimes(effectiveStart, effectiveEnd);
            for (LocalDateTime queryTime : queryTimes) {
                KisDto.TimeDailyChartPriceResponse response = fetchWithRetry(() ->
                        kisApiClient.fetchTimeDailyChartPrice(
                                "J",
                                symbol,
                                queryTime.format(DateTimeFormatter.ofPattern("HHmmss")),
                                queryTime.format(DateTimeFormatter.BASIC_ISO_DATE),
                                "Y",
                                null
                        )
                );

                if (response == null || response.getOutput2() == null) {
                    continue;
                }

                BigDecimal previousClosePrice = toBigDecimal(
                        response.getOutput1() == null ? null : response.getOutput1().getStck_prdy_clpr());

                for (KisDto.TimeDailyChartPriceOutput2 item : response.getOutput2()) {
                    LocalDateTime candleAt = parseDateTime(
                            item.getStck_bsop_date(),
                            item.getStck_cntg_hour()
                    );
                    if (candleAt == null) {
                        continue;
                    }
                    LocalDateTime normalized = normalizeIntradayAt(candleAt);

                    if (normalized.isBefore(normalizedStart) || normalized.isAfter(normalizedEnd)) {
                        continue;
                    }

                    if (normalized.isBefore(sessionStart) || normalized.isAfter(sessionEnd)) {
                        continue;
                    }

                    minuteCandles.putIfAbsent(normalized, PriceCandleDto.Response.builder()
                            .open(toBigDecimal(item.getStck_oprc()))
                            .close(toBigDecimal(item.getStck_prpr()))
                            .high(toBigDecimal(item.getStck_hgpr()))
                            .low(toBigDecimal(item.getStck_lwpr()))
                            .volume(toBigDecimal(item.getCntg_vol()))
                            .value(toBigDecimal(item.getAcml_tr_pbmn()))
                            .stockId(stockId)
                            .timeframe(Timeframe.MINUTE)
                            .at(normalized)
                            .prevDayChangePct(calculateChangeRate(toBigDecimal(item.getStck_prpr()), previousClosePrice))
                            .build());
                }
            }
        }

        return minuteCandles.values().stream()
                .sorted((left, right) -> left.getAt().compareTo(right.getAt()))
                .collect(Collectors.toList());
    }

    private List<PriceCandleDto.Response> fetchDailyCandles(
            String symbol,
            Long stockId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Timeframe timeframe
    ) {
        Map<LocalDateTime, PriceCandleDto.Response> results = new HashMap<>();

        String periodCode = switch (timeframe) {
            case DAY -> "D";
            case WEEK -> "W";
            case MONTH -> "M";
            case YEAR -> "Y";
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };

        LocalDate startDate = startTime.toLocalDate();
        LocalDate cursorEndDate = endTime.toLocalDate();
        int callCount = 0;

        while (!cursorEndDate.isBefore(startDate) && callCount < DAILY_CHART_MAX_CALL_COUNT) {
            callCount++;

            KisDto.DailyItemChartPriceResponse response = kisApiClient.fetchDailyItemChartPrice(
                    "J",
                    symbol,
                    startDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                    cursorEndDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                    periodCode,
                    "1"
            );

            if (response == null || response.getOutput2() == null || response.getOutput2().isEmpty()) {
                break;
            }

            List<KisDto.DailyItemChartPriceOutput2> items = response.getOutput2();
            LocalDate oldestDateInBatch = cursorEndDate;

            for (KisDto.DailyItemChartPriceOutput2 item : items) {
                LocalDateTime candleAt = parseDateTime(item.getStck_bsop_date(), null);
                if (candleAt == null) {
                    continue;
                }
                LocalDateTime normalizedAt = normalizeDateAt(candleAt, timeframe);

                if (normalizedAt.isBefore(startTime) || normalizedAt.isAfter(endTime)) {
                    continue;
                }

                results.putIfAbsent(normalizedAt, PriceCandleDto.Response.builder()
                        .open(toBigDecimal(item.getStck_oprc()))
                        .close(toBigDecimal(item.getStck_clpr()))
                        .high(toBigDecimal(item.getStck_hgpr()))
                        .low(toBigDecimal(item.getStck_lwpr()))
                        .volume(toBigDecimal(item.getAcml_vol()))
                        .value(toBigDecimal(item.getAcml_tr_pbmn()))
                        .stockId(stockId)
                        .timeframe(timeframe)
                        .at(normalizedAt)
                        .prevDayChangePct(calculateDailyChangeRate(item))
                        .build());

                LocalDate candleDate = candleAt.toLocalDate();
                if (candleDate.isBefore(oldestDateInBatch)) {
                    oldestDateInBatch = candleDate;
                }
            }

            if (items.size() < DAILY_CHART_BATCH_LIMIT) {
                break;
            }

            LocalDate nextCursorEndDate = oldestDateInBatch.minusDays(1);
            if (!nextCursorEndDate.isBefore(cursorEndDate)) {
                break;
            }
            cursorEndDate = nextCursorEndDate;
        }

        if (callCount >= DAILY_CHART_MAX_CALL_COUNT) {
            log.warn("Daily candle fetch call limit reached. symbol={}, timeframe={}, startTime={}, endTime={}",
                    symbol, timeframe, startTime, endTime);
        }

        return results.values().stream()
                .sorted((left, right) -> left.getAt().compareTo(right.getAt()))
                .collect(Collectors.toList());
    }

    private LocalDateTime normalizeIntradayAt(LocalDateTime at) {
        return at.withSecond(0).withNano(0);
    }

    private LocalDateTime normalizeDateAt(LocalDateTime at, Timeframe timeframe) {
        return switch (timeframe) {
            case DAY -> at.withHour(0).withMinute(0).withSecond(0).withNano(0);
            case WEEK -> at.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case MONTH -> at.with(TemporalAdjusters.firstDayOfMonth())
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case YEAR -> at.with(TemporalAdjusters.firstDayOfYear())
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            default -> at;
        };
    }

    private LocalDateTime parseDateTime(String date, String time) {
        if (date == null || date.isBlank()) {
            log.debug("Skip candle because date is blank. time={}", time);
            return null;
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            if (time == null || time.isBlank()) {
                return parsedDate.atStartOfDay();
            }

            String normalizedTime = time.length() == 4 ? time + "00" : time;
            LocalTime parsedTime = LocalTime.parse(normalizedTime, DateTimeFormatter.ofPattern("HHmmss"));
            return LocalDateTime.of(parsedDate, parsedTime);
        } catch (Exception ex) {
            log.debug("Skip candle because date/time parsing failed. date={}, time={}", date, time, ex);
            return null;
        }
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private BigDecimal calculateDailyChangeRate(KisDto.DailyItemChartPriceOutput2 item) {
        BigDecimal closePrice = toBigDecimal(item.getStck_clpr());
        BigDecimal signedChange = resolveSignedChange(item.getPrdy_vrss(), item.getPrdy_vrss_sign());
        BigDecimal previousClosePrice = closePrice.subtract(signedChange);
        return calculateChangeRate(closePrice, previousClosePrice);
    }

    private BigDecimal resolveSignedChange(String change, String changeSign) {
        BigDecimal changeValue = toBigDecimal(change);

        if ("4".equals(changeSign) || "5".equals(changeSign)) {
            return changeValue.abs().negate();
        }

        if ("1".equals(changeSign) || "2".equals(changeSign)) {
            return changeValue.abs();
        }

        return changeValue;
    }

    private BigDecimal calculateChangeRate(BigDecimal currentPrice, BigDecimal previousClosePrice) {
        if (previousClosePrice == null || previousClosePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentPrice
                .subtract(previousClosePrice)
                .multiply(ONE_HUNDRED)
                .divide(previousClosePrice, CHANGE_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private List<LocalDate> getTradingDates(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isTradingDay(current)) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private LocalDateTime clampToSessionStart(LocalDateTime time, LocalDateTime sessionStart, LocalDateTime sessionEnd) {
        if (time.isAfter(sessionEnd)) {
            return null;
        }
        if (time.isBefore(sessionStart)) {
            return sessionStart;
        }
        return time;
    }

    private LocalDateTime clampToSessionEnd(LocalDateTime time, LocalDateTime sessionStart, LocalDateTime sessionEnd) {
        if (time.isBefore(sessionStart)) {
            return null;
        }
        if (time.isAfter(sessionEnd)) {
            return sessionEnd;
        }
        return time;
    }

    private List<LocalDateTime> buildQueryTimes(LocalDateTime effectiveStart, LocalDateTime effectiveEnd) {
        List<LocalDateTime> queryTimes = new ArrayList<>();
        LocalDateTime current = effectiveEnd;
        while (!current.isBefore(effectiveStart)) {
            queryTimes.add(current);
            current = current.minusHours(2);
        }
        return queryTimes;
    }

    private KisDto.TimeDailyChartPriceResponse fetchWithRetry(Supplier<KisDto.TimeDailyChartPriceResponse> requestSupplier) {
        int maxAttempts = 3;
        long delayMillis = 200L;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestSupplier.get();
            } catch (Exception ex) {
                if (attempt == maxAttempts) {
                    log.warn("Failed to fetch intraday prices after {} attempts", maxAttempts, ex);
                    return null;
                }
                long jitter = ThreadLocalRandom.current().nextLong(50L, 151L);
                sleep(delayMillis + jitter);
                delayMillis = delayMillis * 2L;
            }
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<PriceCandleDto.Response> bulkFetchCurrentPrices(List<String> stockSymbols) {
        if (stockSymbols == null || stockSymbols.isEmpty()) {
            return List.of();
        }

        // symbol -> stockId 매핑 생성
        List<Stock> stocks = stockRepository.findAllBySymbolIn(stockSymbols);
        Map<String, Long> symbolToStockIdMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getSymbol, Stock::getId));

        List<PriceCandleDto.Response> results = new ArrayList<>();

        // API 제한: 한 번에 최대 30개 종목만 조회 가능
        int batchSize = 30;
        for (int i = 0; i < stockSymbols.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, stockSymbols.size());
            List<String> batchSymbols = stockSymbols.subList(i, endIndex);

            // StockInfo 리스트 생성 (marketCode는 "J"로 고정 - KRX)
            List<KisDto.StockInfo> stockInfos = batchSymbols.stream()
                    .map(symbol -> KisDto.StockInfo.of("J", symbol))
                    .toList();

            try {
                List<KisDto.IntstockMultpriceResponseItem> responseItems =
                        kisApiClient.fetchIntstockMultpriceBatch(stockInfos);

                // 응답을 PriceCandleDto.Response로 변환
                for (KisDto.IntstockMultpriceResponseItem item : responseItems) {
                    String symbol = item.getInter_shrn_iscd();
                    Long stockId = symbolToStockIdMap.get(symbol);
                    
                    if (stockId == null) {
                        log.warn("Stock ID not found for symbol: {}", symbol);
                        continue;
                    }

                    BigDecimal currentPrice = toBigDecimal(item.getInter2_prpr());
                    BigDecimal openPrice = toBigDecimal(item.getInter2_oprc());
                    BigDecimal highPrice = toBigDecimal(item.getInter2_hgpr());
                    BigDecimal lowPrice = toBigDecimal(item.getInter2_lwpr());

                    // 현재가 정보를 캔들 형태로 변환 (현재 시점의 데이터)
                    results.add(PriceCandleDto.Response.builder()
                            .open(openPrice)
                            .close(currentPrice)
                            .high(highPrice)
                            .low(lowPrice)
                            .volume(toBigDecimal(item.getAcml_vol()))
                            .value(toBigDecimal(item.getAcml_tr_pbmn()))
                            .stockId(stockId)
                            .timeframe(Timeframe.MINUTE) // 현재가 조회이므로 MINUTE로 설정
                            .at(LocalDateTime.now().withSecond(0).withNano(0)) // 현재 시점
                            .prevDayChangePct(toBigDecimal(item.getPrdy_ctrt()))
                            .build());
                }
            } catch (Exception e) {
                log.error("Failed to fetch current prices for batch starting at index {}", i, e);
                // 에러가 발생해도 다음 배치 계속 처리
            }
        }

        return results;
    }

}
