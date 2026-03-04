package depth.finvibe.modules.trade.infra.messaging;

import depth.finvibe.modules.trade.application.port.out.TradeEventProducer;
import depth.finvibe.modules.trade.domain.Trade;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeKafkaProducer implements TradeEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TRADE_EXECUTED_TOPIC = "trade.trade-executed.v1";
    private static final String TRADE_CANCELLED_TOPIC = "trade.trade-cancelled.v1";
    private static final String TRADE_RESERVED_TOPIC = "trade.trade-reserved.v1";

    @Override
    public void publishNormalTradeExecutedEvent(Trade trade) {
        log.info("Publishing normal trade executed event for trade: {}", trade.getId());
        TradeExecutedEvent event = createTradeExecutedEvent(trade);
        kafkaTemplate.send(TRADE_EXECUTED_TOPIC, trade.getUserId().toString(), event);
    }

    @Override
    public void publishTradeReservedEvent(Trade trade) {
        log.info("Publishing trade reserved event for trade: {}", trade.getId());
        TradeExecutedEvent event = createTradeExecutedEvent(trade);
        kafkaTemplate.send(TRADE_RESERVED_TOPIC, trade.getUserId().toString(), event);
    }

    @Override
    public void publishTradeCancelledEvent(Trade trade) {
        log.info("Publishing trade cancelled event for trade: {}", trade.getId());
        kafkaTemplate.send(TRADE_CANCELLED_TOPIC, trade.getUserId().toString(), trade.getId());
    }

    private TradeExecutedEvent createTradeExecutedEvent(Trade trade) {
        return TradeExecutedEvent.builder()
                .tradeId(trade.getId())
                .userId(trade.getUserId().toString())
                .type(trade.getTransactionType().name())
                .amount(BigDecimal.valueOf(trade.getAmount()))
                .price(trade.getPrice())
                .stockId(trade.getStockId())
                .name(trade.getStockName())
                .currency("KRW")
                .portfolioId(trade.getPortfolioId())
                .build();
    }
}
