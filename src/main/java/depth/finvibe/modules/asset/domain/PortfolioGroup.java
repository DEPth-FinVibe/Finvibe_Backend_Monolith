package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.asset.domain.error.AssetErrorCode;
import depth.finvibe.common.investment.domain.TimeStampedBaseEntity;
import depth.finvibe.common.error.DomainException;

@Entity
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioGroup extends TimeStampedBaseEntity {
    /**
     * 자산 수량이 "0"이 되었는지 판단할 때만 사용하는 임계값.
     * - 금융 도메인에서 임의의 허용 오차(EPS)를 연산/정산에 쓰는 것은 위험하므로
     * - 여기서는 "삭제(정리)" 판단에 한해서만, 프론트/전송 과정에서 생길 수 있는 미세 잔량을 0으로 간주한다.
     */
    private static final BigDecimal AMOUNT_ZERO_THRESHOLD = new BigDecimal("0.000001");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private UUID userId;

    private String iconCode;

    @Builder.Default
    private Boolean isDefault = false;

    @OneToMany(mappedBy = "portfolioGroup", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "totalCurrentValue", column = @Column(name = "total_current_value")),
        @AttributeOverride(name = "totalProfitLoss", column = @Column(name = "total_profit_loss")),
        @AttributeOverride(name = "totalReturnRate", column = @Column(name = "total_return_rate")),
        @AttributeOverride(name = "calculatedAt", column = @Column(name = "portfolio_valuation_calculated_at"))
    })
    private PortfolioValuation valuation;

    public static PortfolioGroup create(String name, UUID userId, String iconCode) {
        if(name == null || name.isBlank() || userId == null) {
            throw new DomainException(AssetErrorCode.INVALID_PORTFOLIO_GROUP_PARAMS);
        }

        return PortfolioGroup.builder()
            .name(name)
            .userId(userId)
            .iconCode(iconCode)
            .build();
    }

    public static PortfolioGroup createDefault(UUID userId) {
        if(userId == null) {
            throw new DomainException(AssetErrorCode.INVALID_PORTFOLIO_GROUP_PARAMS);
        }

        return PortfolioGroup.builder()
            .name("기본 포트폴리오")
            .userId(userId)
            .iconCode("default_icon")
            .isDefault(true)
            .build();
    }

    public void patch(String name, String iconCode) {
        if(this.isDefault) {
            throw new DomainException(AssetErrorCode.CANNOT_MODIFY_DEFAULT_PORTFOLIO_GROUP);
        }

        if(name != null && !name.isBlank()) {
            this.name = name;
        }
        if(iconCode != null) {
            this.iconCode = iconCode;
        }
    }

    public void register(Asset asset, UUID requesterId) {
        if(!this.userId.equals(requesterId)) {
            throw new DomainException(AssetErrorCode.ONLY_OWNER_CAN_REGISTER_ASSET);
        }

        Optional<Asset> foundAsset = assets.stream()
                .filter(it -> it.getStockId().equals(asset.getStockId()))
                .findFirst();

        if(foundAsset.isPresent()) {
            foundAsset.get().additionalBuy(asset.getAmount(), asset.getTotalPrice());
        } else {
            this.assets.add(asset); // cascade 옵션으로 인해 PortfolioGroup이 저장될 때 Asset도 함께 저장
            asset.setPortfolioGroup(this);
        }
    }

    public Optional<Long> unregister(Long stockId, BigDecimal amount, Money paidMoney, UUID requesterId) {
        if(!this.userId.equals(requesterId)) {
            throw new DomainException(AssetErrorCode.ONLY_OWNER_CAN_UNREGISTER_ASSET);
        }

        Optional<Asset> foundAsset = assets.stream()
                .filter(it -> it.getStockId().equals(stockId))
                .findFirst();

        if(foundAsset.isEmpty()) {
            throw new DomainException(AssetErrorCode.CANNOT_SELL_NON_EXISTENT_ASSET);
        }

        foundAsset.get().partialSell(amount, paidMoney);

        if (isEffectivelyZero(foundAsset.get().getAmount())) {
            Asset removedAsset = foundAsset.get();
            this.assets.remove(removedAsset);
            removedAsset.setPortfolioGroup(null);
            // 삭제 실행은 서비스 계층에서 명시적으로 처리한다.
            return Optional.ofNullable(removedAsset.getId());
        }
        return Optional.empty();
    }

    private boolean isEffectivelyZero(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return amount.abs().compareTo(AMOUNT_ZERO_THRESHOLD) <= 0;
    }

    public void ensureDeletable(UUID requesterUserId) {
        if(this.isDefault) {
            throw new DomainException(AssetErrorCode.CANNOT_DELETE_DEFAULT_PORTFOLIO_GROUP);
        }
        if(!this.userId.equals(requesterUserId)) {
            throw new DomainException(AssetErrorCode.ONLY_OWNER_CAN_DELETE_PORTFOLIO_GROUP);
        }
    }

    public List<Long> transferAssetsTo(PortfolioGroup targetGroup) {
        List<Long> mergedSourceAssetIds = new ArrayList<>();
        for (Asset asset : new ArrayList<>(this.assets)) {
            Optional<Asset> existingAsset = targetGroup.assets.stream()
                    .filter(a -> a.getStockId().equals(asset.getStockId()))
                    .findFirst();

            if (existingAsset.isPresent()) {
                existingAsset.get().additionalBuy(asset.getAmount(), asset.getTotalPrice());
                this.assets.remove(asset);
                asset.setPortfolioGroup(null);
            } else {
                asset.setPortfolioGroup(targetGroup);
                targetGroup.assets.add(asset);
                this.assets.remove(asset);
            }

            if (existingAsset.isPresent() && asset.getId() != null) {
                mergedSourceAssetIds.add(asset.getId());
            }
        }
        return mergedSourceAssetIds;
    }

    public Optional<Long> transferAssetTo(Long assetId, PortfolioGroup targetGroup, UUID requesterId) {
        if (!this.userId.equals(requesterId) || !targetGroup.userId.equals(requesterId)) {
            throw new DomainException(AssetErrorCode.ONLY_OWNER_CAN_TRANSFER_ASSET);
        }

        Optional<Asset> foundAsset = this.assets.stream()
                .filter(asset -> asset.getId() != null && asset.getId().equals(assetId))
                .findFirst();

        if (foundAsset.isEmpty()) {
            throw new DomainException(AssetErrorCode.ASSET_NOT_FOUND);
        }

        Asset sourceAsset = foundAsset.get();
        Long stockId = sourceAsset.getStockId();

        Optional<Asset> targetAsset = targetGroup.assets.stream()
                .filter(asset -> asset.getStockId().equals(stockId))
                .findFirst();

        if (targetAsset.isPresent()) {
            targetAsset.get().additionalBuy(sourceAsset.getAmount(), sourceAsset.getTotalPrice());
            this.assets.remove(sourceAsset);
            sourceAsset.setPortfolioGroup(null);
            return Optional.ofNullable(sourceAsset.getId());
        }

        this.assets.remove(sourceAsset);
        sourceAsset.setPortfolioGroup(targetGroup);
        targetGroup.assets.add(sourceAsset);
        return Optional.empty();
    }

    public void recalculateValuation() {
        List<Asset> valuedAssets = assets.stream()
                .filter(asset -> asset.getValuation() != null)
                .toList();

        if (valuedAssets.isEmpty()) {
            this.valuation = PortfolioValuation.empty();
            return;
        }

        BigDecimal totalPurchaseAmount = valuedAssets.stream()
                .map(asset -> asset.getTotalPrice().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AssetValuation> assetValuations = valuedAssets.stream()
                .map(Asset::getValuation)
                .toList();

        this.valuation = PortfolioValuation.aggregate(assetValuations, totalPurchaseAmount);
    }
}
