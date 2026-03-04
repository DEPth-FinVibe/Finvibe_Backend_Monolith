package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.enums.ReservationType;

public interface ReservationEventPublisher {
    void publishReservationConditionMetEvent(Long tradeId, ReservationType type, Long stockId, Long price);
}
