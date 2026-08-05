package depth.finvibe.modules.market.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.application.port.out.StockPriceEventProducer;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.CurrentPrice;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentPriceServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private HoldingStockRepository holdingStockRepository;

    @Mock
    private CurrentStockWatcherRepository currentStockWatcherRepository;

    @Mock
    private CurrentPriceRepository currentPriceRepository;

    @Mock
    private CurrentPriceEventPublisher currentPriceEventPublisher;

    @Mock
    private StockPriceEventProducer stockPriceEventProducer;

    private CurrentPriceService service;

    @BeforeEach
    void setUp() {
        service = new CurrentPriceService(
                stockRepository,
                holdingStockRepository,
                currentStockWatcherRepository,
                currentPriceRepository,
                currentPriceEventPublisher,
                stockPriceEventProducer
        );
    }

    @Test
    @DisplayName("감시 종목이 없어도 현재가를 저장하고 이벤트를 발행한다")
    void stockPriceUpdated_noWatcher_success() {
        // given
        CurrentPriceUpdatedEvent event = priceEvent(1L, "70000");

        // when
        service.stockPriceUpdated(event);

        // then
        verifyNoInteractions(currentStockWatcherRepository);
        verify(currentPriceRepository).upsertCurrentPrice(any(CurrentPrice.class));
        verify(currentPriceEventPublisher).publish(event);
        verify(stockPriceEventProducer).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    @Test
    @DisplayName("가격이 같으면 중복 Kafka 가격 이벤트를 발행하지 않는다")
    void stockPriceUpdated_samePrice_suppressesDuplicateKafkaEvent() {
        // given
        CurrentPriceUpdatedEvent first = priceEvent(1L, "70000");
        CurrentPriceUpdatedEvent second = priceEvent(1L, "70000");

        // when
        service.stockPriceUpdated(first);
        service.stockPriceUpdated(second);

        // then
        verify(currentPriceRepository, times(2)).upsertCurrentPrice(any(CurrentPrice.class));
        verify(currentPriceEventPublisher, times(2)).publish(any(CurrentPriceUpdatedEvent.class));
        verify(stockPriceEventProducer).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    private CurrentPriceUpdatedEvent priceEvent(Long stockId, String close) {
        BigDecimal price = new BigDecimal(close);
        return CurrentPriceUpdatedEvent.builder()
                .stockId(stockId)
                .at(LocalDateTime.parse("2026-05-29T10:00:00"))
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .prevDayChangePct(BigDecimal.ZERO)
                .volume(BigDecimal.TEN)
                .value(BigDecimal.TEN)
                .build();
    }
}
