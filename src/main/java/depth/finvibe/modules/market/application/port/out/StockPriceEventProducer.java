package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;

public interface StockPriceEventProducer {
	void publishStockPriceUpdated(StockPriceUpdatedEvent event);
}
