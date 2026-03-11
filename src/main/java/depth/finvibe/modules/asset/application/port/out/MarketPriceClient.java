package depth.finvibe.modules.asset.application.port.out;

import depth.finvibe.common.investment.dto.BatchPriceSnapshot;
import java.util.List;

public interface MarketPriceClient {
    List<BatchPriceSnapshot> getBatchPrices(List<Long> stockIds);
}
