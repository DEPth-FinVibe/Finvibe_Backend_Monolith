package depth.finvibe.modules.wallet.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.wallet.application.port.in.WalletEventUseCase;
import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletKafkaConsumer {
    private final WalletEventUseCase walletEventService;

    @KafkaListener(
        topics = "trade.trade-executed.v1", 
        groupId = "wallet-group",
        properties = {
            "spring.json.value.default.type=depth.finvibe.common.investment.dto.TradeExecutedEvent"
        }
    )
    public void consumeTradeExecutedEvent(ConsumerRecord<String, TradeExecutedEvent> record) {
        log.info("Received TradeExecutedEvent from topic: {}", record.topic());
        TradeExecutedEvent event = record.value();
        walletEventService.handleTradeExecutedEvent(event);
    }

    @KafkaListener(
            topics = "user.signup.v1",
            groupId = "wallet-group",
            properties = {
                    "spring.json.value.default.type=depth.finvibe.common.investment.dto.SignUpEvent"
            }
    )
    public void consumeSignUpEvent(ConsumerRecord<String, SignUpEvent> record) {
        log.info("Received SignUpEvent from topic: {}", record.topic());
        SignUpEvent event = record.value();
        walletEventService.handleSignUpEvent(event);
    }
}
