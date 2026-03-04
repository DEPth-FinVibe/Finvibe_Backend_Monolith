package depth.finvibe.modules.trade.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.trade.application.port.in.TradeEventUseCase;
import depth.finvibe.common.investment.dto.ReservationSatisfiedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeKafkaConsumer {
    private final TradeEventUseCase tradeEventService;

    @KafkaListener(
            topics = "market.reservation-satisfied.v1",
            groupId = "trade-group",
            properties = {
                "spring.json.value.default.type=depth.finvibe.common.investment.dto.ReservationSatisfiedEvent"
            }
    )
    public void consumeReservationSatisfiedEvent(ConsumerRecord<String, ReservationSatisfiedEvent> record) {
        log.info("Consumed ReservationSatisfiedEvent from topic: {}, key: {}, offset: {}",
                record.topic(), record.key(), record.offset());
        ReservationSatisfiedEvent event = record.value();
        tradeEventService.processReservedTradeExecution(event);
    }
}
