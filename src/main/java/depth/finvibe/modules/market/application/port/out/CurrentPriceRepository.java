package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.CurrentPrice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CurrentPriceRepository {
    void upsertCurrentPrice(CurrentPrice currentPrice);
    void deleteCurrentPrice(Long stockId);

    List<CurrentPrice> findByStockIds(List<Long> stockIds);

    Map<Long, LocalDateTime> findLastUpdatedAtByStockIds(List<Long> stockIds);
}
