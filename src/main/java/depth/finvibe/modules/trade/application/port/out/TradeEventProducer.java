package depth.finvibe.modules.trade.application.port.out;

import depth.finvibe.modules.trade.domain.Trade;

public interface TradeEventProducer {

    void publishNormalTradeExecutedEvent(Trade trade);
    void publishTradeReservedEvent(Trade trade);
    void publishTradeCancelledEvent(Trade trade);
}
