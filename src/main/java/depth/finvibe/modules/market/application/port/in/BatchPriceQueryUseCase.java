package depth.finvibe.modules.market.application.port.in;

import java.util.List;

import depth.finvibe.common.investment.dto.BatchPriceSnapshot;

public interface BatchPriceQueryUseCase {
    List<BatchPriceSnapshot> getBatchPrices(List<Long> stockIds);
}
