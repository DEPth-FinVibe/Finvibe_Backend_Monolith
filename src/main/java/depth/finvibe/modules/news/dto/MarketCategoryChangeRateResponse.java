package depth.finvibe.modules.news.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketCategoryChangeRateResponse(
        Long categoryId,
        String categoryName,
        BigDecimal averageChangePct,
        Integer stockCount,
        Integer positiveCount,
        Integer negativeCount,
        LocalDateTime updatedAt) {
}
