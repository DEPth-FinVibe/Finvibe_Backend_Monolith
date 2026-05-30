package depth.finvibe.modules.market.infra.event;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SpringEvent를 통해 가격 변동 이벤트를 처리.
 * 노드별로 다루는 stock의 영역이 다르기 때문에 동시성 이슈가 발생하지 않음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketEventConsumer {

    private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
    private final ReservationQueryUseCase reservationQueryUseCase;

    @EventListener
    @Async("priceEventExecutor")
    public void handlePriceUpdateEvent(CurrentPriceUpdatedEvent event) {
        long start = System.currentTimeMillis();
        try {
            currentPriceCommandUseCase.stockPriceUpdated(event);
        } finally {
            log.debug("Price update event handled in {} ms. stockId={}", System.currentTimeMillis() - start, event.getStockId());
        }
    }

    @EventListener
    @Async("reservationEventExecutor")
    public void handlePriceUpdateEventForReservation(CurrentPriceUpdatedEvent event) {
        long start = System.currentTimeMillis();
        try {
            reservationQueryUseCase.reservedStockPriceChanged(event.getStockId(), event.getClose().toBigInteger().longValue());
        } finally {
            log.debug("Reservation price event handled in {} ms. stockId={}", System.currentTimeMillis() - start, event.getStockId());
        }
    }
}
