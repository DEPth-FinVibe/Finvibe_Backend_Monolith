package depth.finvibe.modules.market.application.port.out;

import java.util.List;
import java.util.UUID;

public interface HoldingStockRepository {
    void registerHoldingStock(Long stockId, Long userId);
    void unregisterHoldingStock(Long stockId, Long userId);
    List<Long> findAllDistinctStockIds();
}
