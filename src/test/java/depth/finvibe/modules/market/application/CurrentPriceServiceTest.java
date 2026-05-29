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
	void processesPriceUpdateEvenWhenNoWatcherExists() {
		CurrentPriceUpdatedEvent event = priceEvent(1L, "70000");

		service.stockPriceUpdated(event);

		verifyNoInteractions(currentStockWatcherRepository);
		verify(currentPriceRepository).upsertCurrentPrice(any(CurrentPrice.class));
		verify(currentPriceEventPublisher).publish(event);
		verify(stockPriceEventProducer).publishStockPriceUpdated(any(StockPriceUpdatedEvent.class));
	}

	@Test
	void suppressesDuplicateKafkaPriceEventWhenPriceIsUnchanged() {
		CurrentPriceUpdatedEvent first = priceEvent(1L, "70000");
		CurrentPriceUpdatedEvent second = priceEvent(1L, "70000");

		service.stockPriceUpdated(first);
		service.stockPriceUpdated(second);

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
