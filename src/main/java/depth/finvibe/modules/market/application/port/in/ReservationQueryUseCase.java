package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.common.investment.dto.TradeExecutedEvent;

public interface ReservationQueryUseCase {
    void makeReservation(TradeExecutedEvent event);
    void cancelReservation(Long tradeId);
    void reservedStockPriceChanged(Long stockId, Long newPrice);
}
