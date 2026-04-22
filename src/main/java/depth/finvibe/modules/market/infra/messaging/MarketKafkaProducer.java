package depth.finvibe.modules.market.infra.messaging;

import depth.finvibe.modules.market.application.port.out.ReservationEventPublisher;
import depth.finvibe.modules.market.application.port.out.StockPriceEventProducer;
import depth.finvibe.modules.market.domain.enums.ReservationType;
import depth.finvibe.common.investment.dto.ReservationSatisfiedEvent;
import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.BatchPriceEventProducer;
import depth.finvibe.common.investment.dto.BatchPriceUpdatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaProducer implements BatchPriceEventProducer, ReservationEventPublisher, StockPriceEventProducer {
    private static final String BATCH_PRICE_UPDATED_TOPIC = "market.batch-price-updated.v1";
    private static final String RESERVATION_CONDITION_MET_TOPIC = "market.reservation-satisfied.v1";
    private static final String STOCK_PRICE_UPDATED_TOPIC = "market.stock-price-updated.v1";
    private static final String STOCK_PRICE_UPDATED_DLT = "market.stock-price-updated.v1.DLT";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishBatchPriceUpdated(BatchPriceUpdatedEvent event) {
        kafkaTemplate.send(BATCH_PRICE_UPDATED_TOPIC, event);
    }

    @Override
    public void publishReservationConditionMetEvent(Long tradeId, ReservationType type, Long stockId, Long price) {
        String typeStr = type == ReservationType.BUY ? "BUY" : "SELL";

        ReservationSatisfiedEvent event = ReservationSatisfiedEvent.of(tradeId, typeStr, price);
        kafkaTemplate.send(RESERVATION_CONDITION_MET_TOPIC, event);
    }

    @Override
    public void publishStockPriceUpdated(StockPriceUpdatedEvent event) {
        kafkaTemplate.send(STOCK_PRICE_UPDATED_TOPIC, String.valueOf(event.getStockId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish stock price event: stockId={}, price={}, sending to DLT",
                        event.getStockId(), event.getPrice(), ex);
                    sendToDlt(event);
                }
            });
    }

    private void sendToDlt(StockPriceUpdatedEvent event) {
        kafkaTemplate.send(STOCK_PRICE_UPDATED_DLT, String.valueOf(event.getStockId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send to DLT: stockId={}, price={}. Event lost.",
                        event.getStockId(), event.getPrice(), ex);
                }
            });
    }
}
