package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import java.util.List;

public interface IndexPriceClient {
    List<IndexTimePriceSnapshot> fetchIndexTimePrices(MarketIndexType indexType);
}
