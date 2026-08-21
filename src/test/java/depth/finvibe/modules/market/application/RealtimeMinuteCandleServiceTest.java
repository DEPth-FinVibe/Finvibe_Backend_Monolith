package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeMinuteCandleServiceTest {

    private static final Long STOCK_ID = 4971L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 8, 21, 9, 0);

    @Mock
    private PriceCandleRepository priceCandleRepository;

    private SimpleMeterRegistry meterRegistry;
    private RealtimeMinuteCandleService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RealtimeMinuteCandleService(priceCandleRepository, meterRegistry);
    }

    @Test
    @DisplayName("같은 분의 개별 체결로 OHLCV와 거래대금을 집계한다")
    void recordTrade_sameMinute_aggregatesExecutions() {
        service.recordTrade(event(NINE.plusSeconds(10), "100", "2"));
        service.recordTrade(event(NINE.plusSeconds(20), "110", "3"));
        service.recordTrade(event(NINE.plusSeconds(30), "90", "4"));

        service.flushDirtyCandles();

        ArgumentCaptor<PriceCandle> captor = ArgumentCaptor.forClass(PriceCandle.class);
        verify(priceCandleRepository).upsertRealtimeMinuteCandle(captor.capture());
        PriceCandle candle = captor.getValue();
        assertThat(candle.getAt()).isEqualTo(NINE);
        assertThat(candle.getOpen()).isEqualByComparingTo("100");
        assertThat(candle.getHigh()).isEqualByComparingTo("110");
        assertThat(candle.getLow()).isEqualByComparingTo("90");
        assertThat(candle.getClose()).isEqualByComparingTo("90");
        assertThat(candle.getVolume()).isEqualByComparingTo("9");
        assertThat(candle.getValue()).isEqualByComparingTo("890");

        service.flushDirtyCandles();
        verify(priceCandleRepository).upsertRealtimeMinuteCandle(any());
    }

    @Test
    @DisplayName("다음 분의 첫 체결이 오면 직전 분봉을 즉시 저장한다")
    void recordTrade_nextMinute_flushesCompletedMinute() {
        service.recordTrade(event(NINE.plusSeconds(50), "100", "2"));

        service.recordTrade(event(NINE.plusMinutes(1).plusSeconds(1), "120", "1"));

        ArgumentCaptor<PriceCandle> captor = ArgumentCaptor.forClass(PriceCandle.class);
        verify(priceCandleRepository).upsertRealtimeMinuteCandle(captor.capture());
        assertThat(captor.getValue().getAt()).isEqualTo(NINE);
        assertThat(captor.getValue().getClose()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("주기 저장 실패 시 dirty 상태를 유지해 다음 주기에 재시도한다")
    void flushDirtyCandles_failure_retries() {
        service.recordTrade(event(NINE.plusSeconds(10), "100", "2"));
        doThrow(new IllegalStateException("db unavailable"))
                .doNothing()
                .when(priceCandleRepository).upsertRealtimeMinuteCandle(any());

        service.flushDirtyCandles();
        service.flushDirtyCandles();

        verify(priceCandleRepository, times(2)).upsertRealtimeMinuteCandle(any());
        assertThat(meterRegistry.counter(
                "market.candle.realtime.flush", "result", "failure", "trigger", "periodic").count())
                .isEqualTo(1.0);
    }

    private CurrentPriceUpdatedEvent event(LocalDateTime at, String price, String executionVolume) {
        return CurrentPriceUpdatedEvent.builder()
                .stockId(STOCK_ID)
                .at(at)
                .close(new BigDecimal(price))
                .executionVolume(new BigDecimal(executionVolume))
                .prevDayChangePct(new BigDecimal("1.25"))
                .build();
    }
}
