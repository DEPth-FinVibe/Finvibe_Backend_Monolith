package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "portfolio_performance_snapshot_daily",
        indexes = {
                @Index(name = "idx_portfolio_snapshot_user_date", columnList = "user_id,snapshot_date"),
                @Index(name = "idx_portfolio_snapshot_date", columnList = "snapshot_date")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PortfolioPerformanceSnapshotDaily {
    @EmbeddedId
    private PortfolioPerformanceSnapshotDailyId id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "portfolio_name", nullable = false, length = 100)
    private String portfolioName;

    @Column(name = "total_current_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalCurrentValue;

    @Column(name = "total_profit_loss", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalProfitLoss;

    @Column(name = "total_return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PortfolioPerformanceSnapshotDaily create(
            PortfolioPerformanceSnapshotDailyId id,
            UUID userId,
            String portfolioName,
            BigDecimal totalCurrentValue,
            BigDecimal totalProfitLoss,
            BigDecimal totalReturnRate
    ) {
        return PortfolioPerformanceSnapshotDaily.builder()
                .id(id)
                .userId(userId)
                .portfolioName(portfolioName)
                .totalCurrentValue(totalCurrentValue)
                .totalProfitLoss(totalProfitLoss)
                .totalReturnRate(totalReturnRate)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
