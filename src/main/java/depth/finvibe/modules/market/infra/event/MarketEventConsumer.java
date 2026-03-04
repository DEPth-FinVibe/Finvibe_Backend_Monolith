package depth.finvibe.modules.market.infra.event;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SpringEvent를 통해 가격 변동 이벤트를 처리.
 * 노드별로 다루는 stock의 영역이 다르기 때문에 동시성 이슈가 발생하지 않음.
 */
@Component
@RequiredArgsConstructor
public class MarketEventConsumer {

    private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
    private final ReservationQueryUseCase reservationQueryUseCase;

    @EventListener
    @Async
    public void handlePriceUpdateEvent(CurrentPriceUpdatedEvent event) {
        currentPriceCommandUseCase.stockPriceUpdated(event);
    }

    @EventListener
    @Async
    public void handlePriceUpdateEventForReservation(CurrentPriceUpdatedEvent event) {
        reservationQueryUseCase.reservedStockPriceChanged(event.getStockId(), event.getClose().toBigInteger().longValue());
    }
}
