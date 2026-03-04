package depth.finvibe.modules.market.application.port.out;

import java.time.LocalDate;
import java.util.List;

import depth.finvibe.modules.market.domain.ClosingPrice;

public interface ClosingPriceRepository {
  List<ClosingPrice> findByStockIdsAndTradingDate(List<Long> stockIds, LocalDate tradingDate);

  void saveAll(List<ClosingPrice> closingPrices);

  void deleteAll();
}
