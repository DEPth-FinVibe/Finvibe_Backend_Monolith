package depth.finvibe.modules.market.application.port.out;

import java.util.List;
import java.util.UUID;

public interface HoldingStockRepository {
    void registerHoldingStock(Long stockId, UUID userId);
    void unregisterHoldingStock(Long stockId, UUID userId);
    List<Long> findAllDistinctStockIds();
}
