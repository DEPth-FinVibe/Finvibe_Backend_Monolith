package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.event.AssetRegisteredEvent;
import depth.finvibe.modules.asset.application.event.AssetTransferredEvent;
import depth.finvibe.modules.asset.application.event.AssetUnregisteredEvent;
import depth.finvibe.modules.asset.application.event.PortfolioCreatedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioDeletedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioUpdatedEvent;
import depth.finvibe.modules.asset.application.port.in.AssetCommandUseCase;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.application.port.out.AssetRepository;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.TopHoldingStockCacheRepository;
import depth.finvibe.modules.asset.application.port.out.WalletClient;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.Money;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.domain.error.AssetErrorCode;
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
    private static final Long TOP_HOLDING_STOCK_CACHE_KEY = 0L;

    private final PortfolioGroupRepository portfolioGroupRepository;
    private final AssetRepository assetRepository;
    private final GamificationEventProducer gamificationEventProducer;
    private final TopHoldingStockCacheRepository topHoldingStockCacheRepository;
    private final WalletClient walletClient;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioGroupDto.AssetResponse> getAssetsByPortfolio(Long portfolioId, Long requesterUserId) {
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
    public List<PortfolioGroupDto.PortfolioGroupResponse> getPortfoliosByUser(Long userId) {
        return portfolioGroupRepository.findAllByUserId(userId).stream()
                .map(PortfolioGroupDto.PortfolioGroupResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isExistPortfolio(Long portfolioId, Long userId) {
        return portfolioGroupRepository.existsByIdAndUserId(portfolioId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientStockAmount(Long portfolioId, Long userId, Long stockId, Double amount) {
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
    public TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocks(Long userId) {
        return topHoldingStockCacheRepository.find(TOP_HOLDING_STOCK_CACHE_KEY)
                .map(response -> {
                    meterRegistry.counter("asset.top_holdings.cache.requests", "result", "hit").increment();
                    return response;
                })
                .orElseGet(() -> {
                    meterRegistry.counter("asset.top_holdings.cache.requests", "result", "miss").increment();
                    return getTopHoldingStocksFromSource();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioGroupDto.AssetAllocationResponse getAssetAllocation(Long requesterUserId) {
        List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllByUserIdWithAssets(requesterUserId);

        BigDecimal stockAmount = portfolios.stream()
                .flatMap(portfolio -> portfolio.getAssets().stream())
                .map(this::resolveAssetHoldingAmount)
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

    private BigDecimal resolveAssetHoldingAmount(Asset asset) {
        if (asset.getTotalPrice() != null && asset.getTotalPrice().getAmount() != null) {
            return asset.getTotalPrice().getAmount();
        }
        return BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public void registerAsset(Long portfolioId, PortfolioGroupDto.RegisterAssetRequest request, Long requesterUserId) {
        HoldingMetricsSnapshot beforeSnapshot = getHoldingMetricsSnapshot(requesterUserId);

        PortfolioGroup foundPortfolioGroup = findPortfolioGroupWithAssets(portfolioId);

        Asset toRegister = toAssetEntity(request, requesterUserId);

        foundPortfolioGroup.register(toRegister, requesterUserId);

        Asset registeredAsset = foundPortfolioGroup.getAssets().stream()
                .filter(a -> a.getStockId().equals(request.getStockId()))
                .findFirst()
                .orElseThrow();

        eventPublisher.publishEvent(AssetRegisteredEvent.builder()
                .portfolioId(portfolioId)
                .stockId(request.getStockId())
                .userId(requesterUserId)
                .amount(registeredAsset.getAmount())
                .purchasePriceAmount(registeredAsset.getTotalPrice().getAmount())
                .currency(registeredAsset.getTotalPrice().getCurrency().name())
                .build());

        HoldingMetricsSnapshot afterSnapshot = getHoldingMetricsSnapshot(requesterUserId);
        publishHoldingMetricsIfChanged(requesterUserId, beforeSnapshot, afterSnapshot);
        topHoldingStockCacheRepository.evictByUserId(requesterUserId);
    }

    @Override
    @Transactional
    public void unregisterAsset(Long portfolioId, PortfolioGroupDto.UnregisterAssetRequest request, Long requesterUserId) {
        HoldingMetricsSnapshot beforeSnapshot = getHoldingMetricsSnapshot(requesterUserId);

        PortfolioGroup foundPortfolioGroup = findPortfolioGroupWithAssets(portfolioId);

        Money totalPrice = Money.of(request.getStockPrice(), request.getCurrency());

        boolean fullyRemoved = foundPortfolioGroup.unregister(
                request.getStockId(),
                request.getAmount(),
                totalPrice,
                requesterUserId
        ).map(deletedId -> {
            assetRepository.deleteById(deletedId);
            return true;
        }).orElse(false);

        BigDecimal remainingAmount = BigDecimal.ZERO;
        BigDecimal remainingPurchasePrice = BigDecimal.ZERO;
        String currency = request.getCurrency().name();

        if (!fullyRemoved) {
            Asset remainingAsset = foundPortfolioGroup.getAssets().stream()
                    .filter(a -> a.getStockId().equals(request.getStockId()))
                    .findFirst()
                    .orElseThrow();
            remainingAmount = remainingAsset.getAmount();
            remainingPurchasePrice = remainingAsset.getTotalPrice().getAmount();
            currency = remainingAsset.getTotalPrice().getCurrency().name();
        }

        eventPublisher.publishEvent(AssetUnregisteredEvent.builder()
                .portfolioId(portfolioId)
                .stockId(request.getStockId())
                .userId(requesterUserId)
                .remainingAmount(remainingAmount)
                .remainingPurchasePriceAmount(remainingPurchasePrice)
                .currency(currency)
                .fullyRemoved(fullyRemoved)
                .build());

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
            Long requesterUserId
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

        Asset targetAsset = targetPortfolioGroup.getAssets().stream()
                .filter(a -> a.getStockId().equals(stockId))
                .findFirst()
                .orElseThrow();

        HoldingMetricsSnapshot afterSnapshot = getHoldingMetricsSnapshot(requesterUserId);
        publishHoldingMetricsIfChanged(requesterUserId, beforeSnapshot, afterSnapshot);
        topHoldingStockCacheRepository.evictByUserId(requesterUserId);

        eventPublisher.publishEvent(AssetTransferredEvent.builder()
                .sourcePortfolioId(sourcePortfolioId)
                .targetPortfolioId(request.getTargetPortfolioId())
                .stockId(stockId)
                .merged(merged)
                .targetAmount(targetAsset.getAmount())
                .targetPurchasePriceAmount(targetAsset.getTotalPrice().getAmount())
                .targetCurrency(targetAsset.getTotalPrice().getCurrency().name())
                .build());
    }

    @Override
    @Transactional
    public void createPortfolioGroup(PortfolioGroupDto.CreatePortfolioGroupRequest request, Long requesterUserId) {
        PortfolioGroup toSave = PortfolioGroup.create(
                request.getName(),
                requesterUserId,
                request.getIconCode()
        );
        PortfolioGroup saved = portfolioGroupRepository.save(toSave);

        eventPublisher.publishEvent(PortfolioCreatedEvent.builder()
                .portfolioId(saved.getId())
                .userId(requesterUserId)
                .build());
    }

    @Override
    @Transactional
    public void updatePortfolioGroup(Long portfolioGroupId, PortfolioGroupDto.UpdatePortfolioGroupRequest request, Long requesterUserId) {
        PortfolioGroup existing = findPortfolioGroupWithAssets(portfolioGroupId);

        existing.patch(
                request.getName(),
                request.getIconCode()
        );

        eventPublisher.publishEvent(PortfolioUpdatedEvent.builder()
                .portfolioId(portfolioGroupId)
                .userId(requesterUserId)
                .build());
    }

    @Override
    @Transactional
    public void deletePortfolioGroup(Long portfolioGroupId, Long requesterUserId) {
        PortfolioGroup existing = findPortfolioGroupWithAssets(portfolioGroupId);

        existing.ensureDeletable(requesterUserId);

        List<Long> stockIds = existing.getAssets().stream()
                .map(Asset::getStockId)
                .toList();

        PortfolioGroup defaultGroup = findDefaultPortfolioGroup(requesterUserId);

        List<Long> mergedSourceAssetIds = existing.transferAssetsTo(defaultGroup);
        assetRepository.deleteAllById(mergedSourceAssetIds);

        portfolioGroupRepository.delete(existing);

        eventPublisher.publishEvent(PortfolioDeletedEvent.builder()
                .deletedPortfolioId(portfolioGroupId)
                .defaultPortfolioId(defaultGroup.getId())
                .userId(requesterUserId)
                .stockIds(stockIds)
                .build());
    }

    @Override
    @Transactional
    public void createDefaultPortfolioGroup(Long targetUserId) {
        PortfolioGroup toSave = PortfolioGroup.createDefault(targetUserId);

        if (portfolioGroupRepository.existDefaultByUserId(targetUserId)) {
            throw new DomainException(AssetErrorCode.DEFAULT_PORTFOLIO_GROUP_ALREADY_EXISTS);
        }

        PortfolioGroup saved = portfolioGroupRepository.save(toSave);

        eventPublisher.publishEvent(PortfolioCreatedEvent.builder()
                .portfolioId(saved.getId())
                .userId(targetUserId)
                .build());
    }

    private PortfolioGroup findPortfolioGroupWithAssets(Long id) {
        return portfolioGroupRepository.findByIdWithAssets(id)
                .orElseThrow(() -> new DomainException(AssetErrorCode.PORTFOLIO_GROUP_NOT_FOUND));
    }

    private PortfolioGroup findDefaultPortfolioGroup(Long userId) {
        return portfolioGroupRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new DomainException(AssetErrorCode.DEFAULT_PORTFOLIO_GROUP_NOT_FOUND));
    }

    private Asset toAssetEntity(PortfolioGroupDto.RegisterAssetRequest request, Long requesterUserId) {
        return Asset.create(
                request.getAmount(),
                request.getStockPrice(),
                request.getCurrency(),
                request.getName(),
                request.getStockId(),
                requesterUserId
        );
    }

    private HoldingMetricsSnapshot getHoldingMetricsSnapshot(Long userId) {
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
            Long userId,
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

    private record HoldingMetricsSnapshot(int holdingStockCount, int portfolioWithStocksCount) {
    }
}
