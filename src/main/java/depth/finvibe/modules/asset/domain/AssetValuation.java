package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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
public class AssetValuation {
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal returnRate;
    private LocalDateTime calculatedAt;

    public static AssetValuation calculate(BigDecimal amount, Money totalPrice, BigDecimal currentPrice) {
        BigDecimal currentValue = amount.multiply(currentPrice);
        BigDecimal purchaseAmount = totalPrice.getAmount();
        BigDecimal profitLoss = currentValue.subtract(purchaseAmount);
        BigDecimal returnRate = calculateReturnRate(profitLoss, purchaseAmount);

        return AssetValuation.builder()
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .returnRate(returnRate)
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
