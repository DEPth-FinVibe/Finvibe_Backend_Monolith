package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.common.investment.dto.BatchPriceUpdatedEvent;

public interface BatchPriceEventProducer {
    void publishBatchPriceUpdated(BatchPriceUpdatedEvent event);
}
