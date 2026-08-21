package depth.finvibe.modules.market.infra.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;

import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.application.port.out.MarketDataSubscriptionResult;
import depth.finvibe.modules.market.application.port.out.ReservationRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.infra.lock.ActiveNodeRegistry;
import depth.finvibe.modules.market.infra.lock.SubscriptionOwnershipManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

    private final Set<Long> subscribedStockIds = ConcurrentHashMap.newKeySet();
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
        ) {
            @Override
            ZonedDateTime now() {
                return ZonedDateTime.of(2026, 5, 29, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));
            }
        };
        synchronizer.initMetrics();
        ReflectionTestUtils.setField(synchronizer, "marketProvider", "kis");

        when(activeNodeRegistry.getNodeId()).thenReturn("node-1");
        when(activeNodeRegistry.getActiveNodeCount()).thenReturn(1);
        when(marketDataStreamPort.getAvailableSessionCount()).thenReturn(1);
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(41);
        when(marketDataStreamPort.getRemainingSubscriptionCapacity()).thenAnswer(
                invocation -> 41 - subscribedStockIds.size()
        );
        when(marketDataStreamPort.removeClosedSessions()).thenReturn(0);
        when(marketDataStreamPort.getSubscribedStockIds()).thenAnswer(
                invocation -> Set.copyOf(subscribedStockIds)
        );
        when(marketDataStreamPort.isSubscribed(anyLong())).thenAnswer(
                invocation -> subscribedStockIds.contains(invocation.<Long>getArgument(0))
        );
        when(marketDataStreamPort.subscribe(anyLong(), anyString())).thenAnswer(invocation -> {
            Long stockId = invocation.getArgument(0);
            return subscribedStockIds.add(stockId)
                    ? MarketDataSubscriptionResult.SUBSCRIBED
                    : MarketDataSubscriptionResult.ALREADY_SUBSCRIBED;
        });
        doAnswer(invocation -> {
            subscribedStockIds.remove(invocation.<Long>getArgument(0));
            return null;
        }).when(marketDataStreamPort).unsubscribe(anyLong(), anyString());

        when(ownershipManager.tryAcquireOwnership(anyLong(), anyString())).thenReturn(true);
        when(stockRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long stockId = invocation.getArgument(0);
            return Optional.of(stock(stockId, "SYM" + stockId));
        });
        doAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(3).get())
                .when(distributedLockManager)
                .executeWithLock(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("예약, watcher, 보유 순서로 제한된 용량을 배정한다")
    void syncRealtimeSubscriptions_priorities_reservationThenWatcherThenHolding() {
        // given
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(2);
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of(30L));
        when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(20L));
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(10L));

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        InOrder order = inOrder(marketDataStreamPort);
        order.verify(marketDataStreamPort).subscribe(30L, "SYM30");
        order.verify(marketDataStreamPort).subscribe(20L, "SYM20");
        verify(marketDataStreamPort, never()).subscribe(10L, "SYM10");
    }

    @Test
    @DisplayName("여러 tier에 중복된 종목은 가장 높은 tier에서 한 번만 구독한다")
    void syncRealtimeSubscriptions_duplicateStock_subscribesOnce() {
        // given
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of(1L));
        when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(1L));
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(1L));

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(marketDataStreamPort, times(1)).subscribe(1L, "SYM1");
    }

    @Test
    @DisplayName("같은 tier에서는 실제 구독을 유지하고 남은 자리를 stockId 순서로 채운다")
    void syncRealtimeSubscriptions_sameTier_keepsCurrentThenSortsByStockId() {
        // given
        subscribedStockIds.add(50L);
        LinkedHashSet<Long> subscriptionOrder = subscriptionOrder();
        subscriptionOrder.add(50L);
        when(ownershipManager.isOwnedByNode(50L, "node-1")).thenReturn(true);
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(2);
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(50L, 20L, 10L));

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(marketDataStreamPort).subscribe(10L, "SYM10");
        verify(marketDataStreamPort, never()).subscribe(20L, "SYM20");
        verify(marketDataStreamPort, never()).subscribe(50L, "SYM50");
        assertThat(subscriptionOrder).containsExactly(50L, 10L);
    }

    @Test
    @DisplayName("보유 종목 7384개에서도 세션 용량만큼만 구독과 소유권을 시도한다")
    void syncRealtimeSubscriptions_manyHoldings_limitsAttemptsToCapacity() {
        // given
        List<Long> holdings = LongStream.rangeClosed(1, 7_384).boxed().toList();
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(holdings);

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(marketDataStreamPort, times(41)).subscribe(anyLong(), anyString());
        verify(ownershipManager, times(41)).tryAcquireOwnership(anyLong(), anyString());
        assertThat(subscriptionOrder()).hasSize(41);
    }

    @Test
    @DisplayName("연결된 세션 용량이 0이면 후보와 종목별 락을 조회하지 않는다")
    void syncRealtimeSubscriptions_zeroCapacity_stopsBeforeCandidateLookup() {
        // given
        when(marketDataStreamPort.getAvailableSessionCount()).thenReturn(0);
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(0);

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(reservationRepository, never()).findReservedStockIds();
        verify(currentStockWatcherRepository, never()).findActiveStockIds();
        verify(holdingStockRepository, never()).findAllDistinctStockIds();
        verify(distributedLockManager, never()).executeWithLock(anyString(), any(), any(), any());
        verify(marketDataStreamPort, never()).subscribe(anyLong(), anyString());
    }

    @Test
    @DisplayName("adapter 구독 실패는 성공으로 기록하지 않고 소유권을 해제한다")
    void syncRealtimeSubscriptions_adapterFailure_releasesOwnership() {
        // given
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of(1L));
        when(marketDataStreamPort.subscribe(1L, "SYM1"))
                .thenReturn(MarketDataSubscriptionResult.NO_SESSION);

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(ownershipManager).releaseOwnership(1L, "node-1");
        assertThat(subscriptionOrder()).isEmpty();
    }

    @Test
    @DisplayName("상위 tier 신규 종목은 용량을 차지한 하위 tier 종목을 교체한다")
    void syncRealtimeSubscriptions_higherTier_preemptsLowerTier() {
        // given
        subscribedStockIds.add(1L);
        subscriptionOrder().add(1L);
        when(ownershipManager.isOwnedByNode(1L, "node-1")).thenReturn(true);
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(1);
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of(2L));
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(1L));

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        InOrder order = inOrder(marketDataStreamPort);
        order.verify(marketDataStreamPort).unsubscribe(1L, "SYM1");
        order.verify(marketDataStreamPort).subscribe(2L, "SYM2");
        assertThat(subscriptionOrder()).containsExactly(2L);
    }

    @Test
    @DisplayName("quota가 줄면 낮은 tier의 최근 구독부터 해제한다")
    void syncRealtimeSubscriptions_quotaShrinks_evictsLowerTierFirst() {
        // given
        subscribedStockIds.addAll(Set.of(1L, 2L, 100L));
        subscriptionOrder().addAll(List.of(1L, 2L, 100L));
        when(ownershipManager.isOwnedByNode(anyLong(), anyString())).thenReturn(true);
        when(activeNodeRegistry.getActiveNodeCount()).thenReturn(3);
        when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(List.of(100L));
        when(holdingStockRepository.findAllDistinctStockIds()).thenReturn(List.of(1L, 2L));

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(marketDataStreamPort).unsubscribe(2L, "SYM2");
        verify(marketDataStreamPort).unsubscribe(1L, "SYM1");
        verify(marketDataStreamPort, never()).unsubscribe(100L, "SYM100");
        assertThat(subscriptionOrder()).containsExactly(100L);
    }

    @Test
    @DisplayName("Mock provider에서는 모든 후보를 구독한다")
    void syncRealtimeSubscriptions_mockProvider_ignoresCapacity() {
        // given
        ReflectionTestUtils.setField(synchronizer, "marketProvider", "mock");
        List<Long> watchers = LongStream.rangeClosed(1, 52).boxed().toList();
        when(currentStockWatcherRepository.findActiveStockIds()).thenReturn(watchers);
        when(marketDataStreamPort.getSubscriptionCapacity()).thenReturn(0);

        // when
        synchronizer.syncRealtimeSubscriptions();

        // then
        verify(marketDataStreamPort, times(52)).subscribe(anyLong(), anyString());
    }

    @SuppressWarnings("unchecked")
    private LinkedHashSet<Long> subscriptionOrder() {
        return (LinkedHashSet<Long>) ReflectionTestUtils.getField(synchronizer, "subscriptionOrder");
    }

    private Stock stock(Long id, String symbol) {
        return Stock.builder()
                .id(id)
                .name(symbol)
                .symbol(symbol)
                .build();
    }
}
