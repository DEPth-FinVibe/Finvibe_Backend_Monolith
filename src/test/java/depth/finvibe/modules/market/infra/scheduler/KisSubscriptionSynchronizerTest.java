package depth.finvibe.modules.market.infra.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.application.port.out.ReservationRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.infra.lock.ActiveNodeRegistry;
import depth.finvibe.modules.market.infra.lock.SubscriptionOwnershipManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KisSubscriptionSynchronizerTest {

	@Mock
	private CurrentStockWatcherRepository currentStockWatcherRepository;

	@Mock
	private HoldingStockRepository holdingStockRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private StockRepository stockRepository;

	@Mock
	private DistributedLockManager distributedLockManager;

	@Mock
	private MarketDataStreamPort marketDataStreamPort;

	@Mock
	private ActiveNodeRegistry activeNodeRegistry;

	@Mock
	private SubscriptionOwnershipManager ownershipManager;

	private KisSubscriptionSynchronizer synchronizer;

	@BeforeEach
	void setUp() {
		synchronizer = new KisSubscriptionSynchronizer(
				currentStockWatcherRepository,
				holdingStockRepository,
				reservationRepository,
				stockRepository,
				distributedLockManager,
				marketDataStreamPort,
				activeNodeRegistry,
				ownershipManager,
				new SimpleMeterRegistry()
		);
		synchronizer.initMetrics();
		ReflectionTestUtils.setField(synchronizer, "marketProvider", "mock");

		when(activeNodeRegistry.getNodeId()).thenReturn("node-1");
		when(activeNodeRegistry.getActiveNodeCount()).thenReturn(1);
		when(marketDataStreamPort.getAvailableSessionCount()).thenReturn(1);
		when(marketDataStreamPort.removeClosedSessions()).thenReturn(0);
		when(marketDataStreamPort.getSubscribedStockIds()).thenReturn(Set.of());
		when(ownershipManager.tryAcquireOwnership(any(), anyString())).thenReturn(true);
		doAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(3).get())
				.when(distributedLockManager)
				.executeWithLock(anyString(), any(), any(), any());
	}

	@Test
	void subscribesHoldingStocksAlongsideReservationAndWatcherStocks() {
		when(reservationRepository.findReservedStockIds()).thenReturn(List.of(1L));
		when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(2L));
		when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(3L));
		when(stockRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
				stock(1L, "005930"),
				stock(2L, "000660"),
				stock(3L, "035420")
		));

		synchronizer.syncRealtimeSubscriptions();

		verify(marketDataStreamPort).subscribe(1L, "005930");
		verify(marketDataStreamPort).subscribe(2L, "000660");
		verify(marketDataStreamPort).subscribe(3L, "035420");
	}

	@Test
	void subscribesDuplicateActiveStockOnlyOnce() {
		when(reservationRepository.findReservedStockIds()).thenReturn(List.of(1L));
		when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(1L));
		when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(1L));
		when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(stock(1L, "005930")));

		synchronizer.syncRealtimeSubscriptions();

		verify(marketDataStreamPort).subscribe(1L, "005930");
	}

	@Test
	void subscribesWhenOnlyHoldingStocksExist() {
		when(reservationRepository.findReservedStockIds()).thenReturn(List.of());
		when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(2L));
		when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of());
		when(stockRepository.findAllById(List.of(2L))).thenReturn(List.of(stock(2L, "000660")));

		synchronizer.syncRealtimeSubscriptions();

		verify(marketDataStreamPort).subscribe(2L, "000660");
	}

	private Stock stock(Long id, String symbol) {
		return Stock.builder()
				.id(id)
				.name(symbol)
				.symbol(symbol)
				.build();
	}
}
