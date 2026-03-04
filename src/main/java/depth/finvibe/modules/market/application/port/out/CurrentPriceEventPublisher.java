package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;

public interface CurrentPriceEventPublisher {
    void publish(CurrentPriceUpdatedEvent event);
}
