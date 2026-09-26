package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.application.port.out.StockPriceEventProducer;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.CurrentPrice;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private SimpleMeterRegistry meterRegistry;

    private CurrentPriceService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CurrentPriceService(
                stockRepository,
                holdingStockRepository,
                currentStockWatcherRepository,
                currentPriceRepository,
                currentPriceEventPublisher,
                stockPriceEventProducer,
                meterRegistry
        );
    }

    @Test
    @DisplayName("감시 종목이 없어도 현재가를 저장하고 이벤트를 발행한다")
    void stockPriceUpdated_noWatcher_success() {
        // given
        CurrentPriceUpdatedEvent event = priceEvent(1L, "70000");
        when(currentPriceRepository.saveIfNewer(any(CurrentPrice.class))).thenReturn(OptionalLong.of(1L));

        // when
        service.stockPriceUpdated(event);

        // then
        verifyNoInteractions(currentStockWatcherRepository);
        verify(currentPriceRepository).saveIfNewer(any(CurrentPrice.class));
        verify(currentPriceEventPublisher).publish(event);
        verify(stockPriceEventProducer).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    @Test
    @DisplayName("가격이 같으면 중복 Kafka 가격 이벤트를 발행하지 않는다")
    void stockPriceUpdated_samePrice_suppressesDuplicateKafkaEvent() {
        // given
        CurrentPriceUpdatedEvent first = priceEvent(1L, "70000");
        CurrentPriceUpdatedEvent second = priceEvent(1L, "70000");
        when(currentPriceRepository.saveIfNewer(any(CurrentPrice.class)))
                .thenReturn(OptionalLong.of(1L), OptionalLong.of(2L));

        // when
        service.stockPriceUpdated(first);
        service.stockPriceUpdated(second);

        // then
        verify(currentPriceRepository, times(2)).saveIfNewer(any(CurrentPrice.class));
        verify(currentPriceEventPublisher, times(2)).publish(any(CurrentPriceUpdatedEvent.class));
        verify(stockPriceEventProducer).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    @Test
    @DisplayName("저장소가 부여한 버전을 Pub/Sub과 Kafka 이벤트에 똑같이 싣는다")
    void stockPriceUpdated_assignedVersion_carriedOnBothPaths() {
        // given
        long version = 1_780_016_400_000_003L;
        CurrentPriceUpdatedEvent event = priceEvent(1L, "70000");
        when(currentPriceRepository.saveIfNewer(any(CurrentPrice.class))).thenReturn(OptionalLong.of(version));

        // when
        service.stockPriceUpdated(event);

        // then
        ArgumentCaptor<CurrentPriceUpdatedEvent> published = ArgumentCaptor.forClass(CurrentPriceUpdatedEvent.class);
        ArgumentCaptor<StockPriceUpdatedEvent> produced = ArgumentCaptor.forClass(StockPriceUpdatedEvent.class);
        verify(currentPriceEventPublisher).publish(published.capture());
        verify(stockPriceEventProducer).publishStockPriceUpdated(produced.capture());
        assertThat(published.getValue().getPriceVersion()).isEqualTo(version);
        assertThat(produced.getValue().getPriceVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("저장된 버전보다 이른 틱은 어느 경로에도 발행하지 않고 카운터만 올린다")
    void stockPriceUpdated_staleTick_skipsBothPaths() {
        // given
        when(currentPriceRepository.saveIfNewer(any(CurrentPrice.class))).thenReturn(OptionalLong.empty());

        // when
        service.stockPriceUpdated(priceEvent(1L, "70000"));

        // then
        verify(currentPriceEventPublisher, never()).publish(any(CurrentPriceUpdatedEvent.class));
        verify(stockPriceEventProducer, never()).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
        assertThat(meterRegistry.counter("market.current_price.stale_ticks").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("이른 틱을 건너뛰어도 Kafka 중복 억제 기준 가격은 바뀌지 않는다")
    void stockPriceUpdated_staleTick_keepsLastPublishedPrice() {
        // given
        when(currentPriceRepository.saveIfNewer(any(CurrentPrice.class)))
                .thenReturn(OptionalLong.of(1L), OptionalLong.empty(), OptionalLong.of(2L));

        // when
        service.stockPriceUpdated(priceEvent(1L, "70000"));
        service.stockPriceUpdated(priceEvent(1L, "69000"));
        service.stockPriceUpdated(priceEvent(1L, "70000"));

        // then
        verify(stockPriceEventProducer, times(1)).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    @Test
    @DisplayName("묶음 처리는 저장을 한 번에 하고, 버전을 받은 틱만 순서대로 한 번에 발행한다")
    void stockPricesUpdated_batch_publishesAcceptedInOrder() {
        // given
        CurrentPriceUpdatedEvent first = priceEvent(1L, "70000");
        CurrentPriceUpdatedEvent stale = priceEvent(2L, "50000");
        CurrentPriceUpdatedEvent third = priceEvent(1L, "70100");
        when(currentPriceRepository.saveAllIfNewer(anyList()))
                .thenReturn(List.of(OptionalLong.of(10L), OptionalLong.empty(), OptionalLong.of(11L)));

        // when
        service.stockPricesUpdated(List.of(first, stale, third));

        // then
        verify(currentPriceRepository, never()).saveIfNewer(any(CurrentPrice.class));
        ArgumentCaptor<List<CurrentPriceUpdatedEvent>> published = ArgumentCaptor.forClass(List.class);
        verify(currentPriceEventPublisher).publishAll(published.capture());
        assertThat(published.getValue()).containsExactly(first, third);
        assertThat(first.getPriceVersion()).isEqualTo(10L);
        assertThat(third.getPriceVersion()).isEqualTo(11L);
        assertThat(stale.getPriceVersion()).isNull();
        assertThat(meterRegistry.counter("market.current_price.stale_ticks").count()).isEqualTo(1.0);
        verify(stockPriceEventProducer, times(2)).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
    }

    @Test
    @DisplayName("묶음의 모든 틱이 오래됐으면 아무것도 발행하지 않는다")
    void stockPricesUpdated_allStale_publishesNothing() {
        // given
        when(currentPriceRepository.saveAllIfNewer(anyList())).thenReturn(List.of(OptionalLong.empty()));

        // when
        service.stockPricesUpdated(List.of(priceEvent(1L, "70000")));

        // then
        verify(currentPriceEventPublisher, never()).publishAll(anyList());
        verify(stockPriceEventProducer, never()).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
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
