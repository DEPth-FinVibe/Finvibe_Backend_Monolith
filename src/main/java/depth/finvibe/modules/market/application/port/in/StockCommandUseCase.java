package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.modules.market.dto.StockDto;

import java.util.List;

public interface StockCommandUseCase {
    void bulkUpsertStocks();

    void renewStockCharts();
}
