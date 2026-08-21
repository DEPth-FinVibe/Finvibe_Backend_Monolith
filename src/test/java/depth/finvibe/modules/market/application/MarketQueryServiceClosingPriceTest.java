package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.modules.market.application.port.out.ClosingPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRankingRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.ClosingPrice;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.dto.ClosingPriceDto;
import depth.finvibe.modules.market.dto.MarketStatusDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class MarketQueryServiceClosingPriceTest {

  private final RealMarketClient realMarketClient = mock(RealMarketClient.class);
  private final ClosingPriceRepository closingPriceRepository = mock(ClosingPriceRepository.class);
  private final StockRepository stockRepository = mock(StockRepository.class);
  private final HolidayCalendarService holidayCalendarService = mock(HolidayCalendarService.class);
  private final MarketStatusService marketStatusService = mock(MarketStatusService.class);
  private SimpleMeterRegistry meterRegistry;
  private MarketQueryService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new MarketQueryService(
        mock(PriceCandleRepository.class),
        realMarketClient,
        mock(CurrentPriceRepository.class),
        closingPriceRepository,
        mock(CurrentStockWatcherRepository.class),
        mock(StockRankingRepository.class),
        stockRepository,
        mock(DistributedLockManager.class),
        holidayCalendarService,
        marketStatusService,
        mock(TransactionTemplate.class),
        meterRegistry,
        Clock.system(ZoneId.of("Asia/Seoul"))
    );
  }

  @Test
  @DisplayName("v2 종가 응답은 확보하지 못한 종목을 부분 응답으로 표시한다")
  void getClosingPricesV2_missingPrice_marksPartial() {
    LocalDate tradingDate = LocalDate.of(2026, 8, 20);
    Stock first = stock(1L, "삼성전자", "005930");
    Stock second = stock(2L, "SK하이닉스", "000660");
    ClosingPrice cached = closingPrice(1L, tradingDate);
    when(marketStatusService.getMarketStatus()).thenReturn(MarketStatusDto.Response.from(MarketStatus.CLOSED));
    when(stockRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
    when(holidayCalendarService.getLastCompletedTradingDay(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(tradingDate));
    when(closingPriceRepository.findByStockIdsAndTradingDate(List.of(1L, 2L), tradingDate))
        .thenReturn(List.of(cached));
    when(realMarketClient.bulkFetchCurrentPrices(List.of("000660"))).thenReturn(List.of());

    ClosingPriceDto.BatchResponse response = service.getClosingPricesV2(List.of(1L, 2L));

    assertThat(response.getItems()).extracting(ClosingPriceDto.Response::getStockId).containsExactly(1L);
    assertThat(response.getMissingStockIds()).containsExactly(2L);
    assertThat(response.isPartial()).isTrue();
    assertThat(response.getTradingDate()).isEqualTo(tradingDate);
    assertThat(meterRegistry.counter("market.closing.price.partial.responses", "endpoint", "v2").count())
        .isEqualTo(1.0);
  }

  private Stock stock(Long id, String name, String symbol) {
    return Stock.builder().id(id).name(name).symbol(symbol).categoryId(1L).build();
  }

  private ClosingPrice closingPrice(Long stockId, LocalDate tradingDate) {
    return ClosingPrice.create(
        stockId,
        tradingDate,
        LocalDateTime.of(tradingDate, java.time.LocalTime.of(15, 30)),
        new BigDecimal("70000"),
        new BigDecimal("1.25"),
        new BigDecimal("1000"),
        new BigDecimal("70000000")
    );
  }
}
