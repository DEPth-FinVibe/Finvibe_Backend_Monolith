package depth.finvibe.modules.market.infra.event;

import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.modules.market.application.port.out.ReservationPriceIndex;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * SpringEvent를 통해 가격 변동 이벤트를 처리.
 * 노드별로 다루는 stock의 영역이 다르기 때문에 동시성 이슈가 발생하지 않음.
 * 현재가 저장·발행은 {@link PriceIngestLanes}가 묶음으로 처리한다.
 */
@Slf4j
@Component
public class MarketEventConsumer {

    private final ReservationQueryUseCase reservationQueryUseCase;
    private final ReservationPriceIndex reservationPriceIndex;
    private final Executor reservationEventExecutor;

    public MarketEventConsumer(
            ReservationQueryUseCase reservationQueryUseCase,
            ReservationPriceIndex reservationPriceIndex,
            @Qualifier("reservationEventExecutor") ObjectProvider<Executor> reservationEventExecutor
    ) {
        this.reservationQueryUseCase = reservationQueryUseCase;
        this.reservationPriceIndex = reservationPriceIndex;
        // loadtest 프로필처럼 실행기가 없으면 호출 스레드에서 처리한다.
        this.reservationEventExecutor = reservationEventExecutor.getIfAvailable(() -> Runnable::run);
    }

    /**
     * 틱마다 메모리 인덱스로 체결될 예약이 있을 수 있는지만 본다. 있을 때만 Redis에서 예약을 조회해 체결한다.
     */
    @EventListener
    public void handlePriceUpdateEventForReservation(CurrentPriceUpdatedEvent event) {
        if (event.getStockId() == null || event.getClose() == null) {
            return;
        }
        Long stockId = event.getStockId();
        long price = event.getClose().toBigInteger().longValue();
        if (!reservationPriceIndex.mayTrigger(stockId, price)) {
            return;
        }
        reservationEventExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                reservationQueryUseCase.reservedStockPriceChanged(stockId, price);
            } finally {
                log.debug("Reservation price event handled in {} ms. stockId={}", System.currentTimeMillis() - start, stockId);
            }
        });
    }
}
