package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import depth.finvibe.common.investment.domain.TimeStampedBaseEntity;

@Entity
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends TimeStampedBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "total_price_currency"))
    })
    private Money totalPrice;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "currentValue", column = @Column(name = "current_value")),
        @AttributeOverride(name = "profitLoss", column = @Column(name = "profit_loss")),
        @AttributeOverride(name = "returnRate", column = @Column(name = "return_rate")),
        @AttributeOverride(name = "calculatedAt", column = @Column(name = "valuation_calculated_at"))
    })
    private AssetValuation valuation;

    private String name;

    private Long stockId;

    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.PROTECTED)
    private PortfolioGroup portfolioGroup;

    public void additionalBuy(BigDecimal amount, Money totalPrice) {
        this.amount = this.amount.add(amount);
        this.totalPrice = this.totalPrice.plus(totalPrice);
    }

    public void partialSell(BigDecimal amount, Money totalPrice) {
        this.amount = this.amount.subtract(amount);
        this.totalPrice = this.totalPrice.minus(totalPrice);
    }

    public void updateValuation(BigDecimal currentPrice) {
        this.valuation = AssetValuation.calculate(this.amount, this.totalPrice, currentPrice);
    }

    public static Asset create(BigDecimal amount, BigDecimal unitPrice, Currency currency, String name, Long stockId, UUID userId) {
        Money totalPrice = Money.of(unitPrice.multiply(amount), currency);
        return Asset.builder()
            .amount(amount)
            .totalPrice(totalPrice)
            .name(name)
            .stockId(stockId)
            .userId(userId)
            .build();
    }
}
