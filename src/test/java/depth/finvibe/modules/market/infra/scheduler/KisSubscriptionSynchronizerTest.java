package depth.finvibe.modules.market.infra.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

	@Test
	void subscribesAllHoldingsWhenWatchersExceedQuota() {
		// 10 holdings, 100 watchers, session capacity = 41 (1 session x 41)
		// 모든 보유 종목은 quota 제한 없이 구독되어야 함
		List<Long> holdings = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
		List<Long> watchers = List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L,
				21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L,
				31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L,
				41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L, 50L,
				51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L,
				61L, 62L, 63L, 64L, 65L, 66L, 67L, 68L, 69L, 70L,
				71L, 72L, 73L, 74L, 75L, 76L, 77L, 78L, 79L, 80L,
				81L, 82L, 83L, 84L, 85L, 86L, 87L, 88L, 89L, 90L,
				91L, 92L, 93L, 94L, 95L, 96L, 97L, 98L, 99L, 100L,
				101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L);

		when(reservationRepository.findReservedStockIds()).thenReturn(List.of());
		when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(holdings);
		when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(watchers);

		// stockRepository.findAllById 호출에 동적으로 응답
		when(stockRepository.findAllById(any())).thenAnswer(invocation -> {
			List<Long> ids = invocation.getArgument(0);
			return ids.stream().map(id -> stock(id, "SYM" + id)).toList();
		});

		// subscribe가 여러 번 호출되어도 Mockito가 허용하도록 lenient하게 처리
		when(marketDataStreamPort.isSubscribed(any())).thenReturn(false);

		synchronizer.syncRealtimeSubscriptions();

		// 모든 보유 종목이 구독되었는지 확인 (10개 전부)
		for (Long id : holdings) {
			verify(marketDataStreamPort).subscribe(id, "SYM" + id);
		}

		// watcher는 quota(41) - 보유(10) = 31개까지만 구독됨
		// watcher 11~41까지만 구독되고, 42부터는 구독되지 않음
		for (long id = 11; id <= 41; id++) {
			verify(marketDataStreamPort).subscribe(id, "SYM" + id);
		}
		// 42 이상의 watcher는 quota 초과로 구독되지 않음
		for (long id = 42; id <= 110; id++) {
			verify(marketDataStreamPort, never()).subscribe(id, "SYM" + id);
		}
	}

	@Test
	void neverEvictsHoldingStocks() {
		// 이전 사이클에서 5개 보유 종목 + 5개 watcher 구독 완료된 상태
		java.util.LinkedHashSet<Long> subscriptionOrder = new java.util.LinkedHashSet<>();
		subscriptionOrder.addAll(List.of(1L, 2L, 3L, 4L, 5L)); // 보유 종목
		subscriptionOrder.addAll(List.of(100L, 101L, 102L, 103L, 104L)); // watcher
		ReflectionTestUtils.setField(synchronizer, "subscriptionOrder", subscriptionOrder);

		// stockSymbolCache도 미리 채움
		java.util.Map<Long, String> stockSymbolCache = new java.util.concurrent.ConcurrentHashMap<>();
		stockSymbolCache.put(1L, "005930");
		stockSymbolCache.put(2L, "000660");
		stockSymbolCache.put(3L, "035420");
		stockSymbolCache.put(4L, "005490");
		stockSymbolCache.put(5L, "051910");
		stockSymbolCache.put(100L, "W100");
		stockSymbolCache.put(101L, "W101");
		stockSymbolCache.put(102L, "W102");
		stockSymbolCache.put(103L, "W103");
		stockSymbolCache.put(104L, "W104");
		ReflectionTestUtils.setField(synchronizer, "stockSymbolCache", stockSymbolCache);

		// 보유 종목(1~5)은 이미 이 노드가 소유 중이고, watcher(100~104)는 아님
		when(ownershipManager.isOwnedByNode(any(), anyString())).thenAnswer(invocation -> {
			Long stockId = invocation.getArgument(0);
			return stockId >= 1L && stockId <= 5L;
		});

		// 현재 구독 상태 반영 (subscriptionOrder와 일치)
		when(marketDataStreamPort.getSubscribedStockIds()).thenReturn(
				Set.of(1L, 2L, 3L, 4L, 5L, 100L, 101L, 102L, 103L, 104L));

		// 보유 종목 + watcher, activeNodeCount=100으로 quota=1로 제한
		// totalActiveStocks = [1,2,3,4,5,100,101,102,103,104] = 10개
		// ceil(10/100) = 1, min(1, 1*41) = 1
		when(activeNodeRegistry.getActiveNodeCount()).thenReturn(100);
		when(reservationRepository.findReservedStockIds()).thenReturn(List.of());
		when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
		when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(100L, 101L, 102L, 103L, 104L));

		synchronizer.syncRealtimeSubscriptions();

		// 보유 종목은 절대 unsubscrib 되지 않음
		verify(marketDataStreamPort, never()).unsubscribe(1L, "005930");
		verify(marketDataStreamPort, never()).unsubscribe(2L, "000660");
		verify(marketDataStreamPort, never()).unsubscribe(3L, "035420");
		verify(marketDataStreamPort, never()).unsubscribe(4L, "005490");
		verify(marketDataStreamPort, never()).unsubscribe(5L, "051910");

		// watcher는 모두 unsubscrib 되어야 함 (quota=1 이하로 줄이기 위해)
		verify(marketDataStreamPort).unsubscribe(100L, "W100");
		verify(marketDataStreamPort).unsubscribe(101L, "W101");
		verify(marketDataStreamPort).unsubscribe(102L, "W102");
		verify(marketDataStreamPort).unsubscribe(103L, "W103");
		verify(marketDataStreamPort).unsubscribe(104L, "W104");
	}
}
