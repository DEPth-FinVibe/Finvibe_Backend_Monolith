package depth.finvibe.modules.news.application.port.out;

import java.math.BigDecimal;

public interface MarketCategoryChangeRatePort {
    BigDecimal fetchAverageChangePct(Long categoryId);
}
