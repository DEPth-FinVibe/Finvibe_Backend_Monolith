package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioValuation {
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalReturnRate;
    private LocalDateTime calculatedAt;

    public static PortfolioValuation aggregate(List<AssetValuation> assetValuations, BigDecimal purchaseAmount) {
        BigDecimal totalCurrentValue = assetValuations.stream()
                .map(AssetValuation::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProfitLoss = assetValuations.stream()
                .map(AssetValuation::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReturnRate = calculateReturnRate(totalProfitLoss, purchaseAmount);

        return PortfolioValuation.builder()
                .totalCurrentValue(totalCurrentValue)
                .totalProfitLoss(totalProfitLoss)
                .totalReturnRate(totalReturnRate)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    public static PortfolioValuation empty() {
        return PortfolioValuation.builder()
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalReturnRate(BigDecimal.ZERO)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private static BigDecimal calculateReturnRate(BigDecimal profitLoss, BigDecimal purchaseAmount) {
        if (purchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return profitLoss
                .divide(purchaseAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
