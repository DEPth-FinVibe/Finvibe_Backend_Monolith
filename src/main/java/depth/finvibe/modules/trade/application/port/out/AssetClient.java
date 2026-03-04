package depth.finvibe.modules.trade.application.port.out;

import java.util.UUID;

public interface AssetClient {
    boolean isExistPortfolio(Long portfolioId, UUID userId);

    boolean hasSufficientStockAmount(Long portfolioId, UUID userId, Long stockId, Double amount);
}
