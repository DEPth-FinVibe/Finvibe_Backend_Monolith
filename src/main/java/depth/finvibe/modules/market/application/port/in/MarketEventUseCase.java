package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.common.investment.dto.StockHoldingChangedEvent;

public interface MarketEventUseCase {
    void handleStockHoldingChangedEvent(StockHoldingChangedEvent event);
}
