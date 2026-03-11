package depth.finvibe.modules.market.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.out.IndexPriceClient;
import depth.finvibe.modules.market.application.port.out.IndexTimePriceSnapshot;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexMinuteCandleCacheService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final IndexPriceClient indexPriceClient;
    private final StockRepository stockRepository;
    private final PriceCandleRepository priceCandleRepository;

    @Transactional
    public void cacheLatestMinuteCandles() {
        for (MarketIndexType indexType : MarketIndexType.values()) {
            cacheIndexMinuteCandles(indexType);
        }
    }

    /**
     * 지수 분봉 데이터가 없으면 초기화
     * 애플리케이션 시작 시 InitialMarketDataRunner에서 호출
     *
     * @param indexType 초기화할 지수 타입 (KOSPI, KOSDAQ)
     */
    @Transactional
    public void initializeIndexMinuteCandlesIfEmpty(MarketIndexType indexType) {
        // 1. Stock 조회 또는 생성 (INDEX_KOSPI, INDEX_KOSDAQ 심볼)
        Stock indexStock = getOrCreateIndexStock(indexType);

        // 2. 분봉 데이터 존재 여부 확인
        if (priceCandleRepository.existsByStockIdAndTimeframe(
                indexStock.getId(), Timeframe.MINUTE)) {
            log.debug("지수 분봉 데이터가 이미 존재하여 초기화 스킵. indexType={}", indexType);
            return;
        }

        // 3. 데이터 없으면 KIS API 호출하여 저장
        log.info("지수 분봉 초기화를 위해 KIS API 호출. indexType={}", indexType);
        cacheIndexMinuteCandles(indexType);
    }

    private void cacheIndexMinuteCandles(MarketIndexType indexType) {
        Stock indexStock = getOrCreateIndexStock(indexType);

        List<IndexTimePriceSnapshot> outputs = indexPriceClient.fetchIndexTimePrices(indexType);

        if (outputs == null || outputs.isEmpty()) {
            log.debug("No index minute candles received. indexType={}", indexType);
            return;
        }

        List<PriceCandle> candles = outputs.stream()
                .map(output -> toPriceCandle(indexStock.getId(), output))
                .filter(candle -> candle != null)
                .toList();

        if (candles.isEmpty()) {
            log.debug("No valid index minute candles to save. indexType={}", indexType);
            return;
        }

        List<LocalDateTime> times = candles.stream()
                .map(PriceCandle::getAt)
                .toList();

        Set<LocalDateTime> existingTimes = priceCandleRepository
                .findByStockIdAndTimeframeAndAtIn(indexStock.getId(), Timeframe.MINUTE, times)
                .stream()
                .map(PriceCandle::getAt)
                .collect(Collectors.toSet());

        List<PriceCandle> newCandles = candles.stream()
                .filter(candle -> !existingTimes.contains(candle.getAt()))
                .toList();

        if (newCandles.isEmpty()) {
            log.debug("All index minute candles already cached. indexType={}", indexType);
            return;
        }

        priceCandleRepository.saveAll(newCandles);
        log.info("Cached {} index minute candles. indexType={}", newCandles.size(), indexType);
    }

    private PriceCandle toPriceCandle(Long stockId, IndexTimePriceSnapshot output) {
        LocalDateTime at = parseAt(output.businessDate(), output.contractHour(), output.businessHour());
        if (at == null) {
            return null;
        }

        return PriceCandle.create(
                stockId,
                Timeframe.MINUTE,
                at,
                toBigDecimal(output.openPrice()),
                toBigDecimal(output.highPrice()),
                toBigDecimal(output.lowPrice()),
                toBigDecimal(output.currentPrice()),
                toBigDecimal(output.previousDayChangeRate()),
                toBigDecimal(output.contractVolume()),
                toBigDecimal(output.accumulatedTradeAmount())
        );
    }

    private LocalDateTime parseAt(String stckBsopDate, String stckCntgHour, String bsopHour) {
        if (stckBsopDate == null || stckBsopDate.isBlank()) {
            log.debug("Skip index minute candle because stck_bsop_date is blank. stck_cntg_hour={}, bsop_hour={}",
                    stckCntgHour, bsopHour);
            return null;
        }

        String rawTime = (stckCntgHour != null && !stckCntgHour.isBlank()) ? stckCntgHour : bsopHour;
        if (rawTime == null || rawTime.isBlank()) {
            log.debug("Skip index minute candle because stck_cntg_hour and bsop_hour are blank. stck_bsop_date={}",
                    stckBsopDate);
            return null;
        }

        String normalizedTime = rawTime.length() == 4 ? rawTime + "00" : rawTime;
        try {
            LocalDate date = LocalDate.parse(stckBsopDate, DATE_FORMATTER);
            LocalTime time = LocalTime.parse(normalizedTime, TIME_FORMATTER);
            return LocalDateTime.of(date, time).withSecond(0).withNano(0);
        } catch (Exception ex) {
            log.debug("Failed to parse index minute candle date/time. stck_bsop_date={}, stck_cntg_hour={}, bsop_hour={}",
                    stckBsopDate, stckCntgHour, bsopHour, ex);
            return null;
        }
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private Stock getOrCreateIndexStock(MarketIndexType indexType) {
        return stockRepository.findBySymbol(indexType.getSymbol())
                .orElseGet(() -> {
                    Stock stock = Stock.create(indexType.getDisplayName(), indexType.getSymbol(), null);
                    stockRepository.save(stock);
                    return stock;
                });
    }

}
