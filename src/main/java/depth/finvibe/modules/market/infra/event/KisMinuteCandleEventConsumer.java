package depth.finvibe.modules.market.infra.event;

import depth.finvibe.modules.market.application.RealtimeMinuteCandleService;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.provider", havingValue = "kis", matchIfMissing = true)
public class KisMinuteCandleEventConsumer {

    private final RealtimeMinuteCandleService realtimeMinuteCandleService;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void handlePriceUpdate(CurrentPriceUpdatedEvent event) {
        realtimeMinuteCandleService.recordTrade(event);
    }
}
