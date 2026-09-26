package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;

import java.util.List;

public interface CurrentPriceEventPublisher {
    void publish(CurrentPriceUpdatedEvent event);

    /**
     * 여러 틱을 한 번에 발행한다. 틱은 하나도 빠뜨리지 않고, 같은 종목의 틱은 주어진 순서대로 전달된다.
     */
    default void publishAll(List<CurrentPriceUpdatedEvent> events) {
        events.forEach(this::publish);
    }
}
