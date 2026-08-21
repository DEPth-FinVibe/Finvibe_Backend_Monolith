package depth.finvibe.modules.market.application;

import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeMinuteCandleService {

    private final PriceCandleRepository priceCandleRepository;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Long, MinuteAccumulator> currentCandles = new ConcurrentHashMap<>();

    public void recordTrade(CurrentPriceUpdatedEvent event) {
        if (!isUsableTrade(event)) {
            meterRegistry.counter("market.candle.realtime.dropped", "reason", "invalid_trade").increment();
            return;
        }

        LocalDateTime minute = Timeframe.MINUTE.normalizeStart(event.getAt());
        if (minute.isBefore(MarketHours.sessionStart(minute.toLocalDate()))
                || minute.isAfter(MarketHours.sessionEnd(minute.toLocalDate()))) {
            meterRegistry.counter("market.candle.realtime.dropped", "reason", "outside_session").increment();
            return;
        }

        AtomicReference<MinuteAccumulator> completed = new AtomicReference<>();
        AtomicReference<Boolean> outOfOrder = new AtomicReference<>(false);

        currentCandles.compute(event.getStockId(), (stockId, current) -> {
            if (current == null) {
                return MinuteAccumulator.start(event, minute);
            }

            int order = minute.compareTo(current.minute());
            if (order < 0) {
                outOfOrder.set(true);
                return current;
            }
            if (order == 0) {
                current.add(event);
                return current;
            }

            completed.set(current);
            return MinuteAccumulator.start(event, minute);
        });

        if (outOfOrder.get()) {
            meterRegistry.counter("market.candle.realtime.dropped", "reason", "out_of_order").increment();
            return;
        }

        MinuteAccumulator completedCandle = completed.get();
        if (completedCandle != null) {
            flushCompleted(completedCandle);
        }
        meterRegistry.counter("market.candle.realtime.trades").increment();
    }

    @Scheduled(fixedDelayString = "${market.candle.realtime.flush-interval-ms:5000}")
    public void flushDirtyCandles() {
        currentCandles.forEach(this::flushCurrent);
    }

    @PreDestroy
    public void flushOnShutdown() {
        currentCandles.forEach(this::flushCurrent);
    }

    private void flushCurrent(Long stockId, MinuteAccumulator accumulator) {
        synchronized (accumulator) {
            if (currentCandles.get(stockId) != accumulator || !accumulator.isDirty()) {
                return;
            }
            if (save(accumulator.snapshot(), "periodic")) {
                accumulator.markFlushed();
            }
        }
    }

    private void flushCompleted(MinuteAccumulator accumulator) {
        synchronized (accumulator) {
            if (save(accumulator.snapshot(), "minute_boundary")) {
                accumulator.markFlushed();
            }
        }
    }

    private boolean save(PriceCandle candle, String trigger) {
        try {
            priceCandleRepository.upsertRealtimeMinuteCandle(candle);
            meterRegistry.counter("market.candle.realtime.flush", "result", "success", "trigger", trigger)
                    .increment();
            return true;
        } catch (RuntimeException ex) {
            meterRegistry.counter("market.candle.realtime.flush", "result", "failure", "trigger", trigger)
                    .increment();
            log.warn("실시간 분봉 저장 실패 - stockId: {}, at: {}, trigger: {}",
                    candle.getStockId(), candle.getAt(), trigger, ex);
            return false;
        }
    }

    private boolean isUsableTrade(CurrentPriceUpdatedEvent event) {
        return event != null
                && event.getStockId() != null
                && event.getAt() != null
                && event.getClose() != null
                && event.getClose().signum() > 0
                && event.getExecutionVolume() != null
                && event.getExecutionVolume().signum() >= 0;
    }

    private static final class MinuteAccumulator {
        private final Long stockId;
        private final LocalDateTime minute;
        private final BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private BigDecimal value;
        private BigDecimal prevDayChangePct;
        private boolean dirty;

        private MinuteAccumulator(CurrentPriceUpdatedEvent event, LocalDateTime minute) {
            BigDecimal price = event.getClose();
            BigDecimal executionVolume = event.getExecutionVolume();
            this.stockId = event.getStockId();
            this.minute = minute;
            this.open = price;
            this.high = price;
            this.low = price;
            this.close = price;
            this.volume = executionVolume;
            this.value = price.multiply(executionVolume);
            this.prevDayChangePct = event.getPrevDayChangePct();
            this.dirty = true;
        }

        static MinuteAccumulator start(CurrentPriceUpdatedEvent event, LocalDateTime minute) {
            return new MinuteAccumulator(event, minute);
        }

        synchronized void add(CurrentPriceUpdatedEvent event) {
            BigDecimal price = event.getClose();
            BigDecimal executionVolume = event.getExecutionVolume();
            high = high.max(price);
            low = low.min(price);
            close = price;
            volume = volume.add(executionVolume);
            value = value.add(price.multiply(executionVolume));
            prevDayChangePct = event.getPrevDayChangePct();
            dirty = true;
        }

        LocalDateTime minute() {
            return minute;
        }

        boolean isDirty() {
            return dirty;
        }

        void markFlushed() {
            dirty = false;
        }

        PriceCandle snapshot() {
            return PriceCandle.create(
                    stockId,
                    Timeframe.MINUTE,
                    minute,
                    open,
                    high,
                    low,
                    close,
                    prevDayChangePct,
                    volume,
                    value
            );
        }
    }
}
