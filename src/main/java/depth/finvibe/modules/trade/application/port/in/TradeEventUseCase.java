package depth.finvibe.modules.trade.application.port.in;

import depth.finvibe.common.investment.dto.ReservationSatisfiedEvent;

public interface TradeEventUseCase {
    void processReservedTradeExecution(ReservationSatisfiedEvent event);
}
