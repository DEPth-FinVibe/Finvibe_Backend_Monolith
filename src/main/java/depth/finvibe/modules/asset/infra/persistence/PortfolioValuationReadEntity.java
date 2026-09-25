package depth.finvibe.modules.asset.infra.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "portfolio_valuation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioValuationReadEntity {
    @Id
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "purchased_value", nullable = false)
    private Long purchasedValue;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;

    @Column(name = "profit_rate", nullable = false)
    private Double profitRate;

    @Column(name = "asset_count", nullable = false)
    private Long assetCount;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;
}
