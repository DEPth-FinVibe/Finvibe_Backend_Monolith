package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.event.AssetTransferredEvent;
import depth.finvibe.modules.asset.application.port.in.AssetCommandUseCase;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.application.port.out.AssetRepository;
import depth.finvibe.modules.asset.application.port.out.PortfolioPerformanceSnapshotRepository;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.TopHoldingStockCacheRepository;
import depth.finvibe.modules.asset.application.port.out.WalletClient;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.PortfolioPerformanceSnapshotDaily;
import depth.finvibe.modules.asset.domain.Money;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.domain.error.AssetErrorCode;
import depth.finvibe.modules.asset.domain.enums.PortfolioChartInterval;
import depth.finvibe.modules.asset.dto.PortfolioPerformanceDto;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;
import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.MetricEventType;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;
import depth.finvibe.common.error.DomainException;

@Service
@RequiredArgsConstructor
public class AssetService implements AssetCommandUseCase, AssetQueryUseCase {
    private static final BigDecimal INITIAL_ASSET_BASELINE = BigDecimal.valueOf(10_000_000L);
    private static final int TOP_HOLDING_STOCK_LIMIT = 100;
    private static final UUID TOP_HOLDING_STOCK_CACHE_KEY = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PortfolioGroupRepository portfolioGroupRepository;
    private final AssetRepository assetRepository;
    private final GamificationEventProducer gamificationEventProducer;
    private final TopHoldingStockCacheRepository topHoldingStockCacheRepository;
    private final WalletClient walletClient;
    private final PortfolioPerformanceSnapshotRepository portfolioPerformanceSnapshotRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioGroupDto.AssetResponse> getAssetsByPortfolio(Long portfolioId, UUID requesterUserId) {
        PortfolioGroup portfolioGroup = findPortfolioGroupWithAssets(portfolioId);

        if (!portfolioGroup.getUserId().equals(requesterUserId)) {
            throw new DomainException(AssetErrorCode.ONLY_OWNER_CAN_VIEW_ASSETS);
        }

        return portfolioGroup.getAssets().stream()
                .map(PortfolioGroupDto.AssetResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioGroupDto.PortfolioGroupResponse> getPortfoliosByUser(UUID userId) {
        return portfolioGroupRepository.findAllByUserId(userId).stream()
                .map(PortfolioGroupDto.PortfolioGroupResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioGroupDto.PortfolioComparisonResponse> getPortfolioComparisons(UUID userId) {
        return portfolioGroupRepository.findAllByUserId(userId).stream()
                .map(PortfolioGroupDto.PortfolioComparisonResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isExistPortfolio(Long portfolioId, UUID userId) {
        return portfolioGroupRepository.existsByIdAndUserId(portfolioId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientStockAmount(Long portfolioId, UUID userId, Long stockId, Double amount) {
        if (stockId == null || amount == null) {
            return false;
        }

        PortfolioGroup portfolioGroup = findPortfolioGroupWithAssets(portfolioId);
        if (!portfolioGroup.getUserId().equals(userId)) {
            return false;
        }

        BigDecimal requiredAmount = BigDecimal.valueOf(amount);
        return portfolioGroup.getAssets().stream()
                .filter(asset -> stockId.equals(asset.getStockId()))
                .findFirst()
                .map(asset -> asset.getAmount().compareTo(requiredAmount) >= 0)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocks(UUID userId) {
        return topHoldingStockCacheRepository.find(TOP_HOLDING_STOCK_CACHE_KEY)
                .orElseGet(this::getTopHoldingStocksFromSource);
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioGroupDto.AssetAllocationResponse getAssetAllocation(UUID requesterUserId) {
        List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllByUserIdWithAssets(requesterUserId);

        BigDecimal stockAmount = portfolios.stream()
                .flatMap(portfolio -> portfolio.getAssets().stream())
                .map(this::resolveAssetCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashAmount = BigDecimal.valueOf(walletClient.getWalletByUserId(requesterUserId).getBalance());
        BigDecimal totalAmount = cashAmount.add(stockAmount);
        BigDecimal changeAmount = totalAmount.subtract(INITIAL_ASSET_BASELINE);
        BigDecimal changeRate = changeAmount
                .divide(INITIAL_ASSET_BASELINE, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return PortfolioGroupDto.AssetAllocationResponse.builder()
                .cashAmount(cashAmount)
                .stockAmount(stockAmount)
                .totalAmount(totalAmount)
                .changeAmount(changeAmount)
                .changeRate(changeRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioPerformanceDto.ChartResponse getPortfolioPerformanceChart(
            UUID requesterUserId,
            LocalDate startDate,
            LocalDate endDate,
            PortfolioChartInterval interval
    ) {
        if (requesterUserId == null || startDate == null || endDate == null || interval == null || startDate.isAfter(endDate)) {
            throw new DomainException(AssetErrorCode.INVALID_PORTFOLIO_CHART_DATE_RANGE);
        }

        List<PortfolioPerformanceSnapshotDaily> snapshots = portfolioPerformanceSnapshotRepository
                .findByUserIdAndSnapshotDateBetween(requesterUserId, startDate, endDate);

        Map<Long, String> portfolioNames = portfolioGroupRepository.findAllByUserId(requesterUserId).stream()
                .filter(portfolio -> portfolio.getId() != null)
                .collect(Collectors.toMap(PortfolioGroup::getId, PortfolioGroup::getName, (a, b) -> a));

        Map<Long, List<PortfolioPerformanceSnapshotDaily>> snapshotsByPortfolio = snapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> snapshot.getId().getPortfolioId()));

        List<PortfolioPerformanceDto.PortfolioSeries> portfolios = snapshotsByPortfolio.entrySet().stream()
                .map(entry -> buildPortfolioSeries(entry.getKey(), entry.getValue(), interval, portfolioNames))
                .sorted(Comparator.comparing(PortfolioPerformanceDto.PortfolioSeries::getPortfolioName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();

        List<PortfolioPerformanceDto.Point> total = buildTotalSeries(snapshotsByPortfolio, interval);

        return PortfolioPerformanceDto.ChartResponse.builder()
                .interval(interval)
                .startDate(startDate)
                .endDate(endDate)
                .portfolios(portfolios)
                .total(total)
                .build();
    }

    private TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocksFromSource() {
        List<TopHoldingStockDto.TopHoldingStockResponse> items = portfolioGroupRepository
                .findTopHoldingStocks(TOP_HOLDING_STOCK_LIMIT);

        TopHoldingStockDto.TopHoldingStockListResponse response = TopHoldingStockDto.TopHoldingStockListResponse.builder()
                .totalElements(items.size())
                .items(items)
                .build();
        topHoldingStockCacheRepository.save(TOP_HOLDING_STOCK_CACHE_KEY, response);
        return response;
    }

    private BigDecimal resolveAssetCurrentValue(Asset asset) {
        if (asset.getValuation() != null && asset.getValuation().getCurrentValue() != null) {
            return asset.getValuation().getCurrentValue();
        }
        if (asset.getTotalPrice() != null && asset.getTotalPrice().getAmount() != null) {
            return asset.getTotalPrice().getAmount();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateReturnRate(BigDecimal profitLoss, BigDecimal purchaseAmount) {
        if (purchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profitLoss
                .divide(purchaseAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private PortfolioPerformanceDto.PortfolioSeries buildPortfolioSeries(
            Long portfolioId,
            List<PortfolioPerformanceSnapshotDaily> snapshots,
            PortfolioChartInterval interval,
            Map<Long, String> portfolioNames
    ) {
        TreeMap<LocalDate, PortfolioPerformanceSnapshotDaily> latestByBucket = latestSnapshotsByBucket(snapshots, interval);
        List<PortfolioPerformanceDto.Point> points = latestByBucket.entrySet().stream()
                .map(entry -> toPoint(entry.getKey(), entry.getValue()))
                .toList();

        String portfolioName = portfolioNames.getOrDefault(portfolioId, snapshots.get(0).getPortfolioName());
        return PortfolioPerformanceDto.PortfolioSeries.builder()
                .portfolioId(portfolioId)
                .portfolioName(portfolioName)
                .points(points)
                .build();
    }

    private List<PortfolioPerformanceDto.Point> buildTotalSeries(
            Map<Long, List<PortfolioPerformanceSnapshotDaily>> snapshotsByPortfolio,
            PortfolioChartInterval interval
    ) {
        TreeMap<LocalDate, PeriodAggregate> totalByBucket = new TreeMap<>();

        for (List<PortfolioPerformanceSnapshotDaily> snapshots : snapshotsByPortfolio.values()) {
            TreeMap<LocalDate, PortfolioPerformanceSnapshotDaily> latestByBucket = latestSnapshotsByBucket(snapshots, interval);
            for (Map.Entry<LocalDate, PortfolioPerformanceSnapshotDaily> entry : latestByBucket.entrySet()) {
                LocalDate bucketDate = entry.getKey();
                PortfolioPerformanceSnapshotDaily snapshot = entry.getValue();
                PeriodAggregate aggregate = totalByBucket.computeIfAbsent(bucketDate, key -> new PeriodAggregate());
                aggregate.totalCurrentValue = aggregate.totalCurrentValue.add(snapshot.getTotalCurrentValue());
                aggregate.totalProfitLoss = aggregate.totalProfitLoss.add(snapshot.getTotalProfitLoss());
            }
        }

        return totalByBucket.entrySet().stream()
                .map(entry -> {
                    BigDecimal purchaseAmount = entry.getValue().totalCurrentValue.subtract(entry.getValue().totalProfitLoss);
                    BigDecimal totalReturnRate = calculateReturnRate(entry.getValue().totalProfitLoss, purchaseAmount);
                    return PortfolioPerformanceDto.Point.builder()
                            .periodStartDate(entry.getKey())
                            .totalCurrentValue(entry.getValue().totalCurrentValue)
                            .totalReturnRate(totalReturnRate)
                            .build();
                })
                .toList();
    }

    private TreeMap<LocalDate, PortfolioPerformanceSnapshotDaily> latestSnapshotsByBucket(
            List<PortfolioPerformanceSnapshotDaily> snapshots,
            PortfolioChartInterval interval
    ) {
        TreeMap<LocalDate, PortfolioPerformanceSnapshotDaily> latestByBucket = new TreeMap<>();
        for (PortfolioPerformanceSnapshotDaily snapshot : snapshots) {
            LocalDate snapshotDate = snapshot.getId().getSnapshotDate();
            LocalDate bucketDate = toBucketDate(snapshotDate, interval);
            PortfolioPerformanceSnapshotDaily existing = latestByBucket.get(bucketDate);
            if (existing == null || snapshotDate.isAfter(existing.getId().getSnapshotDate())) {
                latestByBucket.put(bucketDate, snapshot);
            }
        }
        return latestByBucket;
    }

    private LocalDate toBucketDate(LocalDate snapshotDate, PortfolioChartInterval interval) {
        return switch (interval) {
            case DAILY -> snapshotDate;
            case WEEKLY -> snapshotDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> snapshotDate.withDayOfMonth(1);
        };
    }

    private PortfolioPerformanceDto.Point toPoint(LocalDate bucketDate, PortfolioPerformanceSnapshotDaily snapshot) {
        return PortfolioPerformanceDto.Point.builder()
                .periodStartDate(bucketDate)
                .totalCurrentValue(snapshot.getTotalCurrentValue())
                .totalReturnRate(snapshot.getTotalReturnRate())
                .build();
    }

    @Override
    @Transactional
    public void registerAsset(Long portfolioId, PortfolioGroupDto.RegisterAssetRequest request, UUID requesterUserId) {
        HoldingMetricsSnapshot beforeSnapshot = getHoldingMetricsSnapshot(requesterUserId);

        PortfolioGroup foundPortfolioGroup = findPortfolioGroupWithAssets(portfolioId);

        Asset toRegister = toAssetEntity(request, requesterUserId);

        foundPortfolioGroup.register(toRegister, requesterUserId);

        HoldingMetricsSnapshot afterSnapshot = getHoldingMetricsSnapshot(requesterUserId);
        publishHoldingMetricsIfChanged(requesterUserId, beforeSnapshot, afterSnapshot);
        topHoldingStockCacheRepository.evictByUserId(requesterUserId);
    }

    @Override
    @Transactional
    public void unregisterAsset(Long portfolioId, PortfolioGroupDto.UnregisterAssetRequest request, UUID requesterUserId) {
        HoldingMetricsSnapshot beforeSnapshot = getHoldingMetricsSnapshot(requesterUserId);

        PortfolioGroup foundPortfolioGroup = findPortfolioGroupWithAssets(portfolioId);

        Money totalPrice = Money.of(request.getStockPrice(), request.getCurrency());

        foundPortfolioGroup.unregister(
                request.getStockId(),
                request.getAmount(),
                totalPrice,
                requesterUserId
        ).ifPresent(assetRepository::deleteById);

        HoldingMetricsSnapshot afterSnapshot = getHoldingMetricsSnapshot(requesterUserId);
        publishHoldingMetricsIfChanged(requesterUserId, beforeSnapshot, afterSnapshot);
        topHoldingStockCacheRepository.evictByUserId(requesterUserId);
    }

    @Override
    @Transactional
    public void transferAsset(
            Long sourcePortfolioId,
            Long assetId,
            PortfolioGroupDto.TransferAssetRequest request,
            UUID requesterUserId
    ) {
        if (sourcePortfolioId.equals(request.getTargetPortfolioId())) {
            throw new DomainException(AssetErrorCode.SAME_PORTFOLIO_GROUP_TRANSFER_NOT_ALLOWED);
        }

        HoldingMetricsSnapshot beforeSnapshot = getHoldingMetricsSnapshot(requesterUserId);

        PortfolioGroup sourcePortfolioGroup = findPortfolioGroupWithAssets(sourcePortfolioId);
        PortfolioGroup targetPortfolioGroup = findPortfolioGroupWithAssets(request.getTargetPortfolioId());

        // 이동할 자산의 stockId 미리 추출
        Asset transferringAsset = sourcePortfolioGroup.getAssets().stream()
                .filter(asset -> asset.getId() != null && asset.getId().equals(assetId))
                .findFirst()
                .orElseThrow(() -> new DomainException(AssetErrorCode.ASSET_NOT_FOUND));
        Long stockId = transferringAsset.getStockId();

        // 자산 이동 및 병합 여부 판단
        java.util.Optional<Long> deletedAssetId = sourcePortfolioGroup.transferAssetTo(assetId, targetPortfolioGroup, requesterUserId);
        boolean merged = deletedAssetId.isPresent();
        deletedAssetId.ifPresent(assetRepository::deleteById);

        HoldingMetricsSnapshot afterSnapshot = getHoldingMetricsSnapshot(requesterUserId);
        publishHoldingMetricsIfChanged(requesterUserId, beforeSnapshot, afterSnapshot);
        topHoldingStockCacheRepository.evictByUserId(requesterUserId);

        // 이벤트 발행
        AssetTransferredEvent event = AssetTransferredEvent.builder()
                .sourcePortfolioId(sourcePortfolioId)
                .targetPortfolioId(request.getTargetPortfolioId())
                .stockId(stockId)
                .merged(merged)
                .build();
        eventPublisher.publishEvent(event);
    }

    @Override
    @Transactional
    public void createPortfolioGroup(PortfolioGroupDto.CreatePortfolioGroupRequest request, UUID requesterUserId) {
        PortfolioGroup toSave = PortfolioGroup.create(
                request.getName(),
                requesterUserId,
                request.getIconCode()
        );
        portfolioGroupRepository.save(toSave);
    }

    @Override
    @Transactional
    public void updatePortfolioGroup(Long portfolioGroupId, PortfolioGroupDto.UpdatePortfolioGroupRequest request, UUID requesterUserId) {
        PortfolioGroup existing = findPortfolioGroupWithAssets(portfolioGroupId);

        existing.patch(
                request.getName(),
                request.getIconCode()
        );
    }

    @Override
    @Transactional
    public void deletePortfolioGroup(Long portfolioGroupId, UUID requesterUserId) {
        PortfolioGroup existing = findPortfolioGroupWithAssets(portfolioGroupId);

        existing.ensureDeletable(requesterUserId);

        PortfolioGroup defaultGroup = findDefaultPortfolioGroup(requesterUserId);

        List<Long> mergedSourceAssetIds = existing.transferAssetsTo(defaultGroup);
        assetRepository.deleteAllById(mergedSourceAssetIds);

        portfolioGroupRepository.delete(existing);
    }

    @Override
    @Transactional
    public void createDefaultPortfolioGroup(UUID targetUserId) {
        PortfolioGroup toSave = PortfolioGroup.createDefault(targetUserId);

        if (portfolioGroupRepository.existDefaultByUserId(targetUserId)) {
            throw new DomainException(AssetErrorCode.DEFAULT_PORTFOLIO_GROUP_ALREADY_EXISTS);
        }

        portfolioGroupRepository.save(toSave);
    }

    private PortfolioGroup findPortfolioGroupWithAssets(Long id) {
        return portfolioGroupRepository.findByIdWithAssets(id)
                .orElseThrow(() -> new DomainException(AssetErrorCode.PORTFOLIO_GROUP_NOT_FOUND));
    }

    private PortfolioGroup findDefaultPortfolioGroup(UUID userId) {
        return portfolioGroupRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new DomainException(AssetErrorCode.DEFAULT_PORTFOLIO_GROUP_NOT_FOUND));
    }

    private Asset toAssetEntity(PortfolioGroupDto.RegisterAssetRequest request, UUID requesterUserId) {
        return Asset.create(
                request.getAmount(),
                request.getStockPrice(),
                request.getCurrency(),
                request.getName(),
                request.getStockId(),
                requesterUserId
        );
    }

    private HoldingMetricsSnapshot getHoldingMetricsSnapshot(UUID userId) {
        List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllByUserIdWithAssets(userId);
        int holdingStockCount = (int) portfolios.stream()
                .flatMap(portfolio -> portfolio.getAssets().stream())
                .map(Asset::getStockId)
                .distinct()
                .count();
        int portfolioWithStocksCount = (int) portfolios.stream()
                .filter(portfolio -> portfolio.getAssets() != null && !portfolio.getAssets().isEmpty())
                .count();
        return new HoldingMetricsSnapshot(holdingStockCount, portfolioWithStocksCount);
    }

    private void publishHoldingMetricsIfChanged(
            UUID userId,
            HoldingMetricsSnapshot beforeSnapshot,
            HoldingMetricsSnapshot afterSnapshot
    ) {
        if (beforeSnapshot.holdingStockCount() != afterSnapshot.holdingStockCount()) {
            gamificationEventProducer.publishUserMetricUpdatedEvent(UserMetricUpdatedEvent.builder()
                    .userId(userId.toString())
                    .eventType(MetricEventType.HOLDING_STOCK_COUNT_CHANGED)
                    .delta((double) afterSnapshot.holdingStockCount())
                    .occurredAt(Instant.now())
                    .build());
        }

        if (beforeSnapshot.portfolioWithStocksCount() != afterSnapshot.portfolioWithStocksCount()) {
            gamificationEventProducer.publishUserMetricUpdatedEvent(UserMetricUpdatedEvent.builder()
                    .userId(userId.toString())
                    .eventType(MetricEventType.PORTFOLIO_WITH_STOCKS_COUNT_CHANGED)
                    .delta((double) afterSnapshot.portfolioWithStocksCount())
                    .occurredAt(Instant.now())
                    .build());
        }

    }

    private static class PeriodAggregate {
        private BigDecimal totalCurrentValue = BigDecimal.ZERO;
        private BigDecimal totalProfitLoss = BigDecimal.ZERO;
    }

    private record HoldingMetricsSnapshot(int holdingStockCount, int portfolioWithStocksCount) {
    }
}
