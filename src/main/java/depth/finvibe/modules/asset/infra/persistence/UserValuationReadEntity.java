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
@Table(name = "user_valuation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserValuationReadEntity {
    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "purchased_value", nullable = false)
    private Long purchasedValue;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;

    @Column(name = "profit_rate", nullable = false)
    private Double profitRate;

    @Column(name = "portfolio_count", nullable = false)
    private Long portfolioCount;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
