package depth.finvibe.modules.trade.application.port.out;

import java.util.UUID;

public interface AssetClient {
    boolean isExistPortfolio(Long portfolioId, Long userId);

    boolean hasSufficientStockAmount(Long portfolioId, Long userId, Long stockId, Double amount);
}
