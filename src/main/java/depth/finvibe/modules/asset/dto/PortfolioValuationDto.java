package depth.finvibe.modules.asset.dto;

import java.time.Instant;
import java.util.List;

public final class PortfolioValuationDto {
    private PortfolioValuationDto() {
    }

    public record UserSnapshot(
        String userId,
        long purchasedValue,
        long currentValue,
        double profitRate,
        long portfolioCount,
        Instant updatedAt
    ) {
    }

    public record PortfolioSnapshot(
        Long portfolioId,
        long purchasedValue,
        long currentValue,
        double profitRate,
        long assetCount,
        Instant updatedAt
    ) {
    }

    public record UserValuationResponse(
        Long userId,
        long purchasedValue,
        long currentValue,
        double profitRate,
        long portfolioCount,
        Instant updatedAt
    ) {
        public static UserValuationResponse from(Long userId, UserSnapshot snapshot) {
            return new UserValuationResponse(
                userId,
                snapshot.purchasedValue(),
                snapshot.currentValue(),
                snapshot.profitRate(),
                snapshot.portfolioCount(),
                snapshot.updatedAt()
            );
        }

        public static UserValuationResponse zero(Long userId, long portfolioCount) {
            return new UserValuationResponse(userId, 0L, 0L, 0.0, portfolioCount, null);
        }
    }

    public record PortfolioValuationResponse(
        Long portfolioId,
        long purchasedValue,
        long currentValue,
        double profitRate,
        long assetCount,
        Instant updatedAt
    ) {
        public static PortfolioValuationResponse from(PortfolioSnapshot snapshot) {
            return new PortfolioValuationResponse(
                snapshot.portfolioId(),
                snapshot.purchasedValue(),
                snapshot.currentValue(),
                snapshot.profitRate(),
                snapshot.assetCount(),
                snapshot.updatedAt()
            );
        }

        public static PortfolioValuationResponse zero(Long portfolioId) {
            return new PortfolioValuationResponse(portfolioId, 0L, 0L, 0.0, 0L, null);
        }
    }

    public record ValuationsResponse(
        UserValuationResponse user,
        List<PortfolioValuationResponse> portfolios
    ) {
    }
}
