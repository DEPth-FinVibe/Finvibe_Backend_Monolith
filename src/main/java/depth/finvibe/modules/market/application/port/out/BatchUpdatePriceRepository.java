package depth.finvibe.modules.market.application.port.out;

import java.util.List;
import java.util.Optional;

import depth.finvibe.modules.market.domain.BatchUpdatePrice;

public interface BatchUpdatePriceRepository {
  void saveAll(List<BatchUpdatePrice> prices);
  Optional<BatchUpdatePrice> findByStockId(Long stockId);
  List<BatchUpdatePrice> findByStockIds(List<Long> stockIds);
}
