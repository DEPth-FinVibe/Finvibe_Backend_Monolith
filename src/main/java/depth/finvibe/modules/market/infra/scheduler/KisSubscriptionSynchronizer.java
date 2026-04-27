package depth.finvibe.modules.market.infra.scheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.ReservationRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.infra.lock.ActiveNodeRegistry;
import depth.finvibe.modules.market.infra.lock.SubscriptionOwnershipManager;
import depth.finvibe.common.investment.lock.DistributedLockManager;
import depth.finvibe.common.investment.lock.LockAcquisitionException;

/**
 * KIS WebSocket 실시간 가격 구독 상태를 동기화하는 스케줄러입니다.
 * <p>
 * 여러 노드 환경에서 공평한 부하 분산을 위해:
 * 1. Heartbeat 방식으로 활성 노드 수를 파악
 * 2. 노드당 최대 구독 수를 동적으로 계산
 * 3. FIFO 방식으로 초과 구독 해제
 */
@Slf4j
@Component
@Profile("!loadtest")
@RequiredArgsConstructor
public class KisSubscriptionSynchronizer {
    private static final int MAX_SUBSCRIPTIONS_PER_SESSION = 41;
    private static final int UNCHANGED_SYNC_DEBUG_INTERVAL = 12;
    private static final Duration SUBSCRIPTION_LOCK_WAIT = Duration.ofMillis(0);
    private static final Duration SUBSCRIPTION_LOCK_LEASE = Duration.ofSeconds(3);

    private final CurrentStockWatcherRepository currentStockWatcherRepository;
    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final DistributedLockManager distributedLockManager;
    private final MarketDataStreamPort marketDataStreamPort;
    private final ActiveNodeRegistry activeNodeRegistry;
    private final SubscriptionOwnershipManager ownershipManager;
    private final MeterRegistry meterRegistry;

    @Value("${market.provider:kis}")
    private String marketProvider;

    // FIFO 방식으로 구독 순서를 추적 (LinkedHashSet)
    private final LinkedHashSet<Long> subscriptionOrder = new LinkedHashSet<>();
    private final Map<Long, String> stockSymbolCache = new ConcurrentHashMap<>();

    private MarketStatus lastMarketStatus;
    private boolean sessionsUnavailable;
    private SyncLogSnapshot lastSyncLogSnapshot;
    private int unchangedSyncCycleCount;

    private Timer syncTimer;
    private Counter syncErrorCounter;
    private Counter lockFailureCounter;

    @PostConstruct
    public void initMetrics() {
        syncTimer = Timer.builder("kis.sync.execution")
                .description("KIS 구독 동기화 사이클 실행 시간")
                .register(meterRegistry);
        syncErrorCounter = Counter.builder("kis.sync.errors")
                .description("KIS 구독 동기화 사이클 에러 수")
                .register(meterRegistry);
        lockFailureCounter = Counter.builder("kis.sync.lock.failures")
                .description("분산 락 획득 실패 수")
                .register(meterRegistry);
        Gauge.builder("kis.sync.subscriptions.active", subscriptionOrder, LinkedHashSet::size)
                .description("이 노드가 보유한 활성 구독 수")
                .register(meterRegistry);
    }

    private record SubscriptionResult(int successCount, int skipCount, int releasedCount) {
    }

    private record SubscriptionAttempt(boolean isSuccess, boolean isSkipped) {
        static SubscriptionAttempt success() {
            return new SubscriptionAttempt(true, false);
        }

        static SubscriptionAttempt skipped() {
            return new SubscriptionAttempt(false, true);
        }

        static SubscriptionAttempt failed() {
            return new SubscriptionAttempt(false, false);
        }
    }

    private record SyncLogSnapshot(
            int successCount,
            int skipCount,
            int releasedCount,
            int totalCount,
            int maxSubscriptions,
            int currentSubscriptions
    ) {
    }

    @Scheduled(fixedDelayString = "${market.subscription.sync-interval-ms:5000}")
    public void syncRealtimeSubscriptions() {
        syncTimer.record(this::doSyncRealtimeSubscriptions);
    }

    private void doSyncRealtimeSubscriptions() {
        try {
            String nodeId = activeNodeRegistry.getNodeId();

            // Heartbeat 기록
            activeNodeRegistry.recordHeartbeat();

            MarketStatus marketStatus = MarketHours.getStatusAt(now());
            logMarketStatusTransition(marketStatus);

            if (marketStatus != MarketStatus.OPEN && !isMockProvider()) {
                handleMarketClosed(nodeId);
                return;
            }

            ensureSessionsReady();

            // 닫힌 세션 정리
            cleanupClosedSessions();

            reconcileSubscriptionOrder();

            List<Long> reservationStockIds = reservationRepository.findReservedStockIds();
            List<Long> watcherStockIds = currentStockWatcherRepository.findActiveStockIds();
            List<Long> activeStockIds = buildActiveStockIds(reservationStockIds, watcherStockIds);

            if (activeStockIds.isEmpty()) {
                handleEmptyActiveStocks(nodeId);
                return;
            }

            // 노드당 최대 구독 수 계산
            int maxSubscriptionsForNode = calculateMaxSubscriptionsForNode(activeStockIds.size());

            Map<Long, String> stockIdToSymbol = buildStockIdToSymbolMap(activeStockIds);
            SubscriptionResult result = processActiveStocks(
                    activeStockIds,
                    stockIdToSymbol,
                    maxSubscriptionsForNode,
                    nodeId,
                    Set.copyOf(reservationStockIds)
            );
            cleanupInactiveStocks(activeStockIds, nodeId);

            logSyncComplete(result, activeStockIds.size(), maxSubscriptionsForNode);

        } catch (Exception ex) {
            syncErrorCounter.increment();
            log.error("KIS 실시간 가격 구독 동기화 실패", ex);
        }
    }

    private void handleMarketClosed(String nodeId) {
        marketDataStreamPort.closeAllSessions();
        releaseAllSubscriptions(nodeId);
    }

    private boolean isMockProvider() {
        return "mock".equalsIgnoreCase(marketProvider);
    }

    private void ensureSessionsReady() {
        marketDataStreamPort.synchronizeSessions();
        if (marketDataStreamPort.getAvailableSessionCount() == 0) {
            log.debug("KIS WebSocket 세션이 없어 재초기화를 시도합니다.");
            marketDataStreamPort.initializeSessions();
        }

        int availableSessionCount = marketDataStreamPort.getAvailableSessionCount();
        boolean currentlyUnavailable = availableSessionCount == 0;
        if (currentlyUnavailable != sessionsUnavailable) {
            if (currentlyUnavailable) {
                log.info("KIS WebSocket 세션이 모두 비가용 상태로 전환되었습니다.");
            } else {
                log.info("KIS WebSocket 세션이 가용 상태로 복구되었습니다. 세션 수: {}", availableSessionCount);
            }
        }
        sessionsUnavailable = currentlyUnavailable;
    }

    private void reconcileSubscriptionOrder() {
        if (subscriptionOrder.isEmpty()) {
            return;
        }

        Set<Long> subscribedStockIds = marketDataStreamPort.getSubscribedStockIds();
        boolean removed = subscriptionOrder.removeIf(stockId -> !subscribedStockIds.contains(stockId));
        if (removed) {
            log.debug("WebSocket 세션 변경으로 구독 순서를 정리했습니다. 남은 구독: {}", subscriptionOrder.size());
        }
    }

    /**
     * 노드당 최대 구독 수를 계산합니다.
     *
     * @param totalActiveStocks 전체 활성 종목 수
     * @return 현재 노드가 보유할 수 있는 최대 구독 수
     */
    private int calculateMaxSubscriptionsForNode(int totalActiveStocks) {
        int activeNodeCount = activeNodeRegistry.getActiveNodeCount();
        int availableSessionCount = marketDataStreamPort.getAvailableSessionCount();

        if (availableSessionCount == 0) {
            log.debug("사용 가능한 KIS 세션이 없습니다. 구독을 중단합니다.");
            return 0;
        }

        // 세션당 최대 구독 수 제한
        int maxBySession = availableSessionCount * MAX_SUBSCRIPTIONS_PER_SESSION;

        // 노드 간 공평 분배
        int fairShare = (int) Math.ceil((double) totalActiveStocks / activeNodeCount);

        int maxSubscriptions = Math.min(fairShare, maxBySession);

        log.debug("노드당 최대 구독 수 계산 - 활성 노드: {}, 전체 종목: {}, 세션 수: {}, 할당량: {}",
                activeNodeCount, totalActiveStocks, availableSessionCount, maxSubscriptions);

        return maxSubscriptions;
    }

    private void handleEmptyActiveStocks(String nodeId) {
        log.trace("활성 구독 종목이 없어 KIS WebSocket 동기화를 건너뜁니다.");
        unsubscribeAllIfNeeded(nodeId);
        releaseAllSubscriptions(nodeId);
    }

    private Map<Long, String> buildStockIdToSymbolMap(List<Long> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> uniqueStockIds = new LinkedHashSet<>(stockIds);
        Map<Long, String> result = new HashMap<>(uniqueStockIds.size());

        List<Long> missingStockIds = uniqueStockIds.stream()
                .filter(stockId -> !stockSymbolCache.containsKey(stockId))
                .toList();

        if (!missingStockIds.isEmpty()) {
            List<Stock> stocks = stockRepository.findAllById(missingStockIds);
            for (Stock stock : stocks) {
                stockSymbolCache.put(stock.getId(), stock.getSymbol());
            }
        }

        for (Long stockId : uniqueStockIds) {
            String symbol = stockSymbolCache.get(stockId);
            if (symbol != null) {
                result.put(stockId, symbol);
            }
        }

        return result;
    }

    private SubscriptionResult processActiveStocks(
            List<Long> activeStockIds,
            Map<Long, String> stockIdToSymbol,
            int maxSubscriptionsForNode,
            String nodeId,
            Set<Long> reservationStockIds
    ) {
        log.debug("KIS WebSocket 구독 동기화 시작 - 활성 종목 수: {}, 최대 구독 수: {}",
                activeStockIds.size(), maxSubscriptionsForNode);

        int successCount = 0;
        int skipCount = 0;
        int releasedCount = 0;

        // 1. 초과 구독 해제 (FIFO 방식)
        releasedCount = releaseExcessSubscriptions(maxSubscriptionsForNode, stockIdToSymbol, nodeId, reservationStockIds);

        // 2. 신규 구독 처리
        for (Long stockId : activeStockIds) {
            boolean ownedByNode = ownershipManager.isOwnedByNode(stockId, nodeId);
            if (subscriptionOrder.contains(stockId) && !ownedByNode) {
                releaseLocalSubscription(stockId, stockIdToSymbol, nodeId);
            }

            if (ownedByNode) {
                SubscriptionAttempt attempt = ensureOwnedSubscription(stockId, stockIdToSymbol, nodeId);
                if (attempt.isSuccess()) {
                    successCount++;
                } else if (attempt.isSkipped()) {
                    skipCount++;
                }
                continue;
            }

            // 최대 구독 수 체크 (신규 구독에만 적용)
            if (subscriptionOrder.size() >= maxSubscriptionsForNode) {
                log.debug("노드 최대 구독 수 도달 - 현재: {}, 최대: {}", subscriptionOrder.size(), maxSubscriptionsForNode);
                break;
            }

            SubscriptionAttempt attempt = trySubscribeStock(stockId, stockIdToSymbol, nodeId);
            if (attempt.isSuccess()) {
                successCount++;
            } else if (attempt.isSkipped()) {
                skipCount++;
            }
        }

        return new SubscriptionResult(successCount, skipCount, releasedCount);
    }

    /**
     * 초과 구독을 FIFO 방식으로 해제합니다.
     *
     * @param maxSubscriptions 최대 구독 수
     * @param stockIdToSymbol  종목 ID-심볼 매핑
     * @return 해제된 구독 수
     */
    private int releaseExcessSubscriptions(
            int maxSubscriptions,
            Map<Long, String> stockIdToSymbol,
            String nodeId,
            Set<Long> reservationStockIds
    ) {
        int releasedCount = 0;

        while (subscriptionOrder.size() > maxSubscriptions) {
            Long oldestStockId = findOldestNonReservation(reservationStockIds);
            if (oldestStockId == null) {
                oldestStockId = subscriptionOrder.iterator().next();
            }
            String symbol = stockIdToSymbol.get(oldestStockId);

            if (symbol == null) {
                symbol = resolveSymbol(oldestStockId, stockIdToSymbol);
            }

            if (symbol != null) {
                try {
                    marketDataStreamPort.unsubscribe(oldestStockId, symbol);
                    ownershipManager.releaseOwnership(oldestStockId, nodeId);
                    subscriptionOrder.remove(oldestStockId);
                    releasedCount++;
                    log.info("할당량 초과로 구독 해제 (FIFO) - stockId: {}, symbol: {}, 남은 구독: {}",
                            oldestStockId, symbol, subscriptionOrder.size());
                } catch (Exception ex) {
                    log.error("초과 구독 해제 실패 - stockId: {}", oldestStockId, ex);
                    subscriptionOrder.remove(oldestStockId); // 실패해도 추적 목록에서 제거
                }
            } else {
                log.warn("초과 구독 해제 중 심볼을 찾을 수 없음 - stockId: {}", oldestStockId);
                ownershipManager.releaseOwnership(oldestStockId, nodeId);
                subscriptionOrder.remove(oldestStockId);
            }
        }

        return releasedCount;
    }

    private Long findOldestNonReservation(Set<Long> reservationStockIds) {
        for (Long stockId : subscriptionOrder) {
            if (!reservationStockIds.contains(stockId)) {
                return stockId;
            }
        }
        return null;
    }

    private List<Long> buildActiveStockIds(List<Long> reservationStockIds, List<Long> watcherStockIds) {
        if (reservationStockIds.isEmpty()) {
            return watcherStockIds;
        }
        if (watcherStockIds.isEmpty()) {
            return reservationStockIds;
        }

        LinkedHashSet<Long> merged = new LinkedHashSet<>(reservationStockIds);
        merged.addAll(watcherStockIds);
        return merged.stream().toList();
    }

    private void releaseLocalSubscription(
            Long stockId,
            Map<Long, String> stockIdToSymbol,
            String nodeId
    ) {
        String symbol = stockIdToSymbol.get(stockId);
        if (symbol == null) {
            symbol = resolveSymbol(stockId, stockIdToSymbol);
        }

        try {
            if (symbol != null) {
                marketDataStreamPort.unsubscribe(stockId, symbol);
            }
        } catch (Exception ex) {
            log.error("소유권 상실로 구독 해제 실패 - stockId: {}", stockId, ex);
        }

        ownershipManager.releaseOwnership(stockId, nodeId);
        subscriptionOrder.remove(stockId);
    }

    private SubscriptionAttempt ensureOwnedSubscription(
            Long stockId,
            Map<Long, String> stockIdToSymbol,
            String nodeId
    ) {
        ownershipManager.renewOwnership(stockId, nodeId);

        if (subscriptionOrder.contains(stockId)) {
            return SubscriptionAttempt.skipped();
        }

        String symbol = stockIdToSymbol.get(stockId);
        if (symbol == null) {
            log.warn("종목 심볼을 찾을 수 없어 소유권을 해제합니다 - stockId: {}", stockId);
            ownershipManager.releaseOwnership(stockId, nodeId);
            return SubscriptionAttempt.failed();
        }

        return withSubscriptionLock(stockId, () -> {
            if (subscriptionOrder.contains(stockId)) {
                return SubscriptionAttempt.skipped();
            }

            try {
                marketDataStreamPort.subscribe(stockId, symbol);
                subscriptionOrder.add(stockId);
                logNewSubscription(stockId, symbol);
                return SubscriptionAttempt.success();
            } catch (Exception ex) {
                log.error("기존 소유 종목 구독 처리 실패 - stockId: {}", stockId, ex);
                ownershipManager.releaseOwnership(stockId, nodeId);
                subscriptionOrder.remove(stockId);
                return SubscriptionAttempt.failed();
            }
        });
    }

    private SubscriptionAttempt trySubscribeStock(
            Long stockId,
            Map<Long, String> stockIdToSymbol,
            String nodeId
    ) {
        return withSubscriptionLock(stockId, () -> {
            if (!ownershipManager.tryAcquireOwnership(stockId, nodeId)) {
                return SubscriptionAttempt.skipped();
            }

            String symbol = stockIdToSymbol.get(stockId);

            if (symbol == null) {
                log.warn("종목 심볼을 찾을 수 없어 구독을 건너뜁니다 - stockId: {}", stockId);
                ownershipManager.releaseOwnership(stockId, nodeId);
                return SubscriptionAttempt.failed();
            }

            try {
                marketDataStreamPort.subscribe(stockId, symbol);
                subscriptionOrder.add(stockId);
                logNewSubscription(stockId, symbol);
                return SubscriptionAttempt.success();
            } catch (Exception ex) {
                log.error("종목 구독 처리 실패 - stockId: {}", stockId, ex);
                ownershipManager.releaseOwnership(stockId, nodeId);
                subscriptionOrder.remove(stockId);
                return SubscriptionAttempt.failed();
            }
        });
    }

    private SubscriptionAttempt withSubscriptionLock(Long stockId, Supplier<SubscriptionAttempt> task) {
        String lockKey = "market:subscription:lock:" + stockId;
        try {
            return distributedLockManager.executeWithLock(
                    lockKey,
                    SUBSCRIPTION_LOCK_WAIT,
                    SUBSCRIPTION_LOCK_LEASE,
                    task
            );
        } catch (LockAcquisitionException ex) {
            lockFailureCounter.increment();
            log.trace("구독 락 획득 실패 - stockId: {}", stockId);
            return SubscriptionAttempt.skipped();
        }
    }

    private void logNewSubscription(Long stockId, String symbol) {
        if (!marketDataStreamPort.isSubscribed(stockId)) {
            log.debug("KIS 실시간 신규 구독 성공 - stockId: {}, symbol: {}", stockId, symbol);
        }
    }

    private void cleanupInactiveStocks(List<Long> activeStockIds, String nodeId) {
        Set<Long> activeStockIdSet = Set.copyOf(activeStockIds);
        Set<Long> subscribedStockIds = marketDataStreamPort.getSubscribedStockIds();

        List<Long> inactiveStockIds = subscribedStockIds.stream()
                .filter(stockId -> !activeStockIdSet.contains(stockId))
                .toList();

        if (inactiveStockIds.isEmpty()) {
            return;
        }

        Map<Long, String> inactiveStockIdToSymbol = buildStockIdToSymbolMap(inactiveStockIds);
        unsubscribeStocks(inactiveStockIds, inactiveStockIdToSymbol, nodeId);
    }

    private void unsubscribeStocks(
            List<Long> stockIds,
            Map<Long, String> stockIdToSymbol,
            String nodeId
    ) {
        int unsubscribeCount = 0;

        for (Long stockId : stockIds) {
            try {
                String symbol = stockIdToSymbol.get(stockId);
                if (symbol != null) {
                    marketDataStreamPort.unsubscribe(stockId, symbol);
                    unsubscribeCount++;
                    log.debug("비활성 종목 구독 해제 - stockId: {}, symbol: {}", stockId, symbol);
                }

                ownershipManager.releaseOwnership(stockId, nodeId);
                subscriptionOrder.remove(stockId);
            } catch (Exception ex) {
                log.error("비활성 종목 구독 해제 실패 - stockId: {}", stockId, ex);
            }
        }

        if (unsubscribeCount > 0) {
            log.info("비활성 종목 구독 해제 완료 - 해제 수: {}", unsubscribeCount);
        }
    }

    private void logSyncComplete(SubscriptionResult result, int totalCount, int maxSubscriptions) {
        SyncLogSnapshot currentSnapshot = new SyncLogSnapshot(
                result.successCount(),
                result.skipCount(),
                result.releasedCount(),
                totalCount,
                maxSubscriptions,
                subscriptionOrder.size()
        );

        if (!currentSnapshot.equals(lastSyncLogSnapshot)) {
            log.info("KIS WebSocket 구독 상태 변경 - 성공: {}, 스킵(다른 노드): {}, 해제(FIFO): {}, 전체: {}, 최대: {}, 현재: {}",
                    result.successCount(), result.skipCount(), result.releasedCount(),
                    totalCount, maxSubscriptions, subscriptionOrder.size());
            lastSyncLogSnapshot = currentSnapshot;
            unchangedSyncCycleCount = 0;
            return;
        }

        unchangedSyncCycleCount++;
        if (unchangedSyncCycleCount % UNCHANGED_SYNC_DEBUG_INTERVAL == 0) {
            log.debug("KIS WebSocket 구독 동기화 상태 동일 - {}회 연속 변화 없음", unchangedSyncCycleCount);
        }
    }

    private void logMarketStatusTransition(MarketStatus currentStatus) {
        if (currentStatus == lastMarketStatus) {
            return;
        }

        if (currentStatus == MarketStatus.OPEN) {
            log.info("장 상태가 OPEN으로 전환되어 KIS WebSocket 구독 동기화를 재개합니다.");
        } else {
            log.info("장 상태가 OPEN이 아니어서 KIS WebSocket 구독 동기화를 중단합니다. 상태: {}", currentStatus);
        }
        lastMarketStatus = currentStatus;
    }

    /**
     * 활성 종목이 없을 때 모든 구독을 해제합니다.
     */
    private void unsubscribeAllIfNeeded(String nodeId) {
        Set<Long> subscribedStockIds = marketDataStreamPort.getSubscribedStockIds();
        if (subscribedStockIds.isEmpty()) {
            return;
        }

        log.info("활성 종목이 없어 모든 구독을 해제합니다 - 구독 수: {}", subscribedStockIds.size());
        Map<Long, String> stockIdToSymbol = buildStockIdToSymbolMap(subscribedStockIds.stream().toList());

        for (Long stockId : subscribedStockIds) {
            try {
                String symbol = stockIdToSymbol.get(stockId);
                if (symbol != null) {
                    marketDataStreamPort.unsubscribe(stockId, symbol);
                }
                ownershipManager.releaseOwnership(stockId, nodeId);
            } catch (Exception ex) {
                log.error("전체 구독 해제 중 오류 - stockId: {}", stockId, ex);
            }
        }
    }

    /**
     * 현재 노드가 보유한 모든 구독을 해제합니다.
     */
    private void releaseAllSubscriptions(String nodeId) {
        if (subscriptionOrder.isEmpty()) {
            return;
        }

        log.debug("모든 구독 해제 시작 - 구독 수: {}", subscriptionOrder.size());
        for (Long stockId : List.copyOf(subscriptionOrder)) {
            try {
                ownershipManager.releaseOwnership(stockId, nodeId);
            } catch (Exception ex) {
                log.error("구독 해제 중 오류 - stockId: {}", stockId, ex);
            }
        }
        subscriptionOrder.clear();
    }

    /**
     * 연결이 끊긴 WebSocket 세션을 정리합니다.
     * 닫힌 세션으로 인한 오류를 방지하기 위해 주기적으로 호출됩니다.
     */
    private void cleanupClosedSessions() {
        try {
            int removedCount = marketDataStreamPort.removeClosedSessions();
            if (removedCount > 0) {
                log.warn("닫힌 WebSocket 세션 감지 및 제거 - 제거된 세션 수: {}", removedCount);
            }
        } catch (Exception ex) {
            log.error("닫힌 세션 정리 중 오류 발생", ex);
        }
    }

    private String resolveSymbol(Long stockId, Map<Long, String> stockIdToSymbol) {
        String cachedSymbol = stockIdToSymbol.get(stockId);
        if (cachedSymbol != null) {
            return cachedSymbol;
        }

        cachedSymbol = stockSymbolCache.get(stockId);
        if (cachedSymbol != null) {
            return cachedSymbol;
        }

        String symbol = stockRepository.findById(stockId)
                .map(Stock::getSymbol)
                .orElse(null);
        if (symbol != null) {
            stockSymbolCache.put(stockId, symbol);
        }
        return symbol;
    }

    ZonedDateTime now() {
        return ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
