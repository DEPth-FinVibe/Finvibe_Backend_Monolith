package depth.finvibe.modules.asset.infra.messaging;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.port.in.AssetEventUseCase;
import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

@Component
@Profile("!mock-market")
@RequiredArgsConstructor
public class KafkaConsumer {
    private final AssetEventUseCase assetEventService;

    @KafkaListener(
        topics = "trade.trade-executed.v1", 
        groupId = "asset-group",
        properties = {
            "spring.json.value.default.type=depth.finvibe.common.investment.dto.TradeExecutedEvent"
        }
    )
    public void consumeTradeExecutedEvent(ConsumerRecord<String, TradeExecutedEvent> record) {
        TradeExecutedEvent event = record.value();
        assetEventService.handleTradeExecutedEvent(event);
    }

    @KafkaListener(
        topics = "user.signup.v1", 
        groupId = "asset-group",
        properties = {
            "spring.json.value.default.type=depth.finvibe.common.investment.dto.SignUpEvent"
        }
    )
    public void consumeSignUpEvent(ConsumerRecord<String, SignUpEvent> record) {
        SignUpEvent event = record.value();
        assetEventService.handleSignUpEvent(event);
    }

}
