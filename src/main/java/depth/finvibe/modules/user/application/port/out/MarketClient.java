package depth.finvibe.modules.user.application.port.out;

import java.util.Optional;

public interface MarketClient {
    Optional<String> getStockNameByStockId(Long stockId);
}
