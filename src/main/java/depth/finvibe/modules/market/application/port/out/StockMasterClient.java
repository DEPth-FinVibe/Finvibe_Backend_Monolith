package depth.finvibe.modules.market.application.port.out;

import java.util.List;

import depth.finvibe.modules.market.dto.StockDto;

public interface StockMasterClient {
	List<StockDto.RealMarketStockResponse> fetchStocks();
}
