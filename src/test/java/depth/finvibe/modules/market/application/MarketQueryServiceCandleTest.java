package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.common.investment.lock.LockAcquisitionException;
import depth.finvibe.modules.market.application.port.out.CandleFetchResult;
import depth.finvibe.modules.market.application.port.out.ClosingPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRankingRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketQueryServiceCandleTest {

    private static final Long STOCK_ID = 4971L;
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDateTime NINE_OCLOCK = TRADING_DATE.atTime(9, 0);

    @Mock
    private PriceCandleRepository priceCandleRepository;
    @Mock
    private RealMarketClient realMarketClient;
    @Mock
    private DistributedLockManager distributedLockManager;
    @Mock
    private HolidayCalendarService holidayCalendarService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SimpleMeterRegistry meterRegistry;
    private MarketQueryService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new MarketQueryService(
                priceCandleRepository,
                realMarketClient,
                mock(CurrentPriceRepository.class),
                mock(ClosingPriceRepository.class),
                mock(CurrentStockWatcherRepository.class),
                mock(StockRankingRepository.class),
                mock(StockRepository.class),
                distributedLockManager,
                holidayCalendarService,
                mock(MarketStatusService.class),
                transactionTemplate,
                meterRegistry,
                Clock.fixed(TRADING_DATE.atTime(10, 1, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"))
        );

        doAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get())
                .when(distributedLockManager)
                .executeWithLock(anyString(), any(Duration.class), any(Duration.class), any());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(holidayCalendarService.getTradingDayStatus(TRADING_DATE)).thenReturn(TradingDayStatus.OPEN);
    }

    @Test
    @DisplayName("KIS 전체 실패 시 실제 캐시가 있으면 캐시를 반환한다")
    void getStockCandles_providerFailed_cached_returnsCache() {
        PriceCandle cached = actualCandle(NINE_OCLOCK);
        when(priceCandleRepository.findExisting(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK.plusMinutes(1), Timeframe.MINUTE))
                .thenReturn(List.of(cached));
        when(realMarketClient.fetchPriceCandles(
                STOCK_ID, NINE_OCLOCK.plusMinutes(1), NINE_OCLOCK.plusMinutes(1), Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.failed());

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK.plusMinutes(1), Timeframe.MINUTE);

        assertThat(result).extracting(PriceCandleDto.Response::getAt).containsExactly(NINE_OCLOCK);
        assertThat(meterRegistry.counter(
                "market.candle.cache.fallback", "timeframe", "MINUTE", "reason", "provider").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("KIS 전체 실패 시 실제 캐시가 없으면 캔들 전용 오류를 발생시킨다")
    void getStockCandles_providerFailed_noCache_throwsServiceUnavailableError() {
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of());
        when(realMarketClient.fetchPriceCandles(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.failed());

        assertThatThrownBy(() -> service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode())
                .isEqualTo(MarketErrorCode.CANDLE_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    @DisplayName("KIS 부분 성공 시 성공한 캔들을 저장하고 반환한다")
    void getStockCandles_providerPartial_savesAndReturnsCandles() {
        PriceCandleDto.Response fetched = candleResponse(NINE_OCLOCK);
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of());
        when(realMarketClient.fetchPriceCandles(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.partial(List.of(fetched)));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).extracting(PriceCandleDto.Response::getAt).containsExactly(NINE_OCLOCK);
        verify(priceCandleRepository).saveAll(any());
        assertThat(meterRegistry.counter("market.candle.provider.partial", "timeframe", "MINUTE").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("기존 결측 마커는 coverage에서 제외하고 실제 캔들로 복원한다")
    void getStockCandles_existingMissing_restoresActualCandle() {
        PriceCandle missing = PriceCandle.createMissing(STOCK_ID, Timeframe.MINUTE, NINE_OCLOCK);
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of(missing));
        when(realMarketClient.fetchPriceCandles(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.complete(List.of(candleResponse(NINE_OCLOCK))));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).hasSize(1);
        assertThat(missing.getIsMissing()).isFalse();
        assertThat(missing.getClose()).isEqualByComparingTo("70500");
        verify(priceCandleRepository).saveAll(List.of(missing));
    }

    @Test
    @DisplayName("KIS 분봉은 기존 캐시가 있어도 완료 구간을 REST 결과로 보정한다")
    void getStockCandles_kisMinute_replacesCompletedCachedCandle() {
        ReflectionTestUtils.setField(service, "marketProvider", "kis");
        PriceCandle cached = actualCandle(NINE_OCLOCK);
        PriceCandleDto.Response authoritative = candleResponse(NINE_OCLOCK);
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of(cached));
        when(realMarketClient.fetchPriceCandles(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.complete(List.of(authoritative)));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).singleElement()
                .extracting(PriceCandleDto.Response::getClose)
                .isEqualTo(new BigDecimal("70500"));
        assertThat(cached.getClose()).isEqualByComparingTo("70500");
        verify(priceCandleRepository).saveAll(List.of(cached));
    }

    @Test
    @DisplayName("KIS REST는 현재 진행 중인 분봉을 조회하지 않는다")
    void getStockCandles_kisMinute_currentMinute_skipsProvider() {
        ReflectionTestUtils.setField(service, "marketProvider", "kis");
        LocalDateTime currentMinute = TRADING_DATE.atTime(10, 1);
        when(priceCandleRepository.findExisting(
                STOCK_ID, currentMinute, currentMinute, Timeframe.MINUTE)).thenReturn(List.of());

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, currentMinute, currentMinute, Timeframe.MINUTE);

        assertThat(result).isEmpty();
        verify(realMarketClient, never()).fetchPriceCandles(any(), any(), any(), any());
    }

    @Test
    @DisplayName("확인된 휴장일은 분봉 제공자를 호출하지 않는다")
    void getStockCandles_closedDay_skipsProvider() {
        when(holidayCalendarService.getTradingDayStatus(TRADING_DATE)).thenReturn(TradingDayStatus.CLOSED);
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of());

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).isEmpty();
        verify(realMarketClient, never()).fetchPriceCandles(any(), any(), any(), any());
    }

    @Test
    @DisplayName("달력 UNKNOWN이면 평일 fallback으로 KIS 조회를 계속한다")
    void getStockCandles_calendarUnknown_queriesProvider() {
        when(holidayCalendarService.getTradingDayStatus(TRADING_DATE)).thenReturn(TradingDayStatus.UNKNOWN);
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of());
        when(realMarketClient.fetchPriceCandles(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.complete(List.of(candleResponse(NINE_OCLOCK))));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).hasSize(1);
        assertThat(meterRegistry.counter("market.candle.calendar.unknown").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("분산 락 획득 실패 시 실제 캐시가 있으면 캐시를 반환한다")
    void getStockCandles_lockFailed_cached_returnsCache() {
        PriceCandle cached = actualCandle(NINE_OCLOCK);
        when(distributedLockManager.executeWithLock(
                anyString(), any(Duration.class), any(Duration.class), any()))
                .thenThrow(new LockAcquisitionException("stock:candle", 10_000L));
        when(priceCandleRepository.findExisting(STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE))
                .thenReturn(List.of(cached));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, NINE_OCLOCK, NINE_OCLOCK, Timeframe.MINUTE);

        assertThat(result).hasSize(1);
        assertThat(meterRegistry.counter(
                "market.candle.cache.fallback", "timeframe", "MINUTE", "reason", "lock").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("분봉 조회 범위는 평일 장 운영 시간으로 제한한다")
    void getStockCandles_minuteRange_usesTradingSessionOnly() {
        LocalDateTime requestStart = TRADING_DATE.atStartOfDay();
        LocalDateTime requestEnd = TRADING_DATE.plusDays(2).atTime(23, 59);
        LocalDateTime sessionStart = TRADING_DATE.atTime(9, 0);
        LocalDateTime sessionEnd = TRADING_DATE.atTime(15, 30);
        when(priceCandleRepository.findExisting(
                STOCK_ID, requestStart, requestEnd.withSecond(0).withNano(0), Timeframe.MINUTE))
                .thenReturn(List.of());
        when(realMarketClient.fetchPriceCandles(STOCK_ID, sessionStart, sessionEnd, Timeframe.MINUTE))
                .thenReturn(CandleFetchResult.complete(List.of(candleResponse(sessionStart))));

        List<PriceCandleDto.Response> result = service.getStockCandles(
                STOCK_ID, requestStart, requestEnd, Timeframe.MINUTE);

        assertThat(result).hasSize(1);
        verify(realMarketClient).fetchPriceCandles(STOCK_ID, sessionStart, sessionEnd, Timeframe.MINUTE);
        verify(holidayCalendarService).getTradingDayStatus(TRADING_DATE);
        verify(holidayCalendarService, never()).getTradingDayStatus(TRADING_DATE.plusDays(1));
        verify(holidayCalendarService, never()).getTradingDayStatus(TRADING_DATE.plusDays(2));
    }

    private PriceCandle actualCandle(LocalDateTime at) {
        return PriceCandle.create(
                STOCK_ID,
                Timeframe.MINUTE,
                at,
                new BigDecimal("70000"),
                new BigDecimal("71000"),
                new BigDecimal("69000"),
                new BigDecimal("70500"),
                new BigDecimal("1.25"),
                new BigDecimal("1000"),
                new BigDecimal("70500000")
        );
    }

    private PriceCandleDto.Response candleResponse(LocalDateTime at) {
        return PriceCandleDto.Response.builder()
                .stockId(STOCK_ID)
                .timeframe(Timeframe.MINUTE)
                .at(at)
                .open(new BigDecimal("70000"))
                .high(new BigDecimal("71000"))
                .low(new BigDecimal("69000"))
                .close(new BigDecimal("70500"))
                .prevDayChangePct(new BigDecimal("1.25"))
                .volume(new BigDecimal("1000"))
                .value(new BigDecimal("70500000"))
                .build();
    }
}
