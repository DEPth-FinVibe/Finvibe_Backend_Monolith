package depth.finvibe.modules.market.infra.messaging;

import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!mock-market")
@RequiredArgsConstructor
public class MarketKafkaConsumer {
    private final ReservationQueryUseCase reservationQueryUseCase;

    @KafkaListener(
            topics = "trade.trade-reserved.v1",
            groupId = "market-group",
            properties = {
                    "spring.json.value.default.type=depth.finvibe.common.investment.dto.TradeExecutedEvent"
            }
    )
    public void consumeTradeReservedEvent(ConsumerRecord<String, TradeExecutedEvent> record) {
        log.info("Consumed TradeReservedEvent: {}", record.key());
        TradeExecutedEvent event = record.value();
        reservationQueryUseCase.makeReservation(event);
    }

    @KafkaListener(
            topics = "trade.trade-cancelled.v1",
            groupId = "market-group",
            properties = {
                    "spring.json.value.default.type=java.lang.Long"
            }
    )
    public void consumeTradeReserveCanceledEvent(ConsumerRecord<String, Long> record) {
        log.info("Consumed TradeReserveCancelledEvent: {}", record.key());
        Long tradeId = record.value();
        reservationQueryUseCase.cancelReservation(tradeId);
    }
}
