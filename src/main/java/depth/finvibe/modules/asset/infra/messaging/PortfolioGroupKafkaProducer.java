package depth.finvibe.modules.asset.infra.messaging;

import depth.finvibe.common.investment.dto.PortfolioGroupChangedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioCreatedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioDeletedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioGroupKafkaProducer {

    private static final String PORTFOLIO_GROUP_CHANGED_TOPIC = "asset.portfolio-group-changed.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishPortfolioCreatedEvent(PortfolioCreatedEvent event) {
        publish(PortfolioGroupChangedEvent.EventType.CREATED, event.getUserId().toString(), event.getPortfolioId(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishPortfolioUpdatedEvent(PortfolioUpdatedEvent event) {
        publish(PortfolioGroupChangedEvent.EventType.UPDATED, event.getUserId().toString(), event.getPortfolioId(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishPortfolioDeletedEvent(PortfolioDeletedEvent event) {
        publish(
                PortfolioGroupChangedEvent.EventType.DELETED,
                event.getUserId().toString(),
                event.getDeletedPortfolioId(),
                event.getDefaultPortfolioId()
        );
    }

    private void publish(PortfolioGroupChangedEvent.EventType eventType, String userId, Long portfolioId, Long targetPortfolioId) {
        PortfolioGroupChangedEvent event = PortfolioGroupChangedEvent.builder()
                .eventType(eventType)
                .userId(userId) // 추후 정수 기반 UserID로 변경
                .portfolioId(portfolioId)
                .targetPortfolioId(targetPortfolioId)
                .occurredAt(Instant.now())
                .build();

        log.info("Publishing portfolio group changed event: eventType={}, portfolioId={}", eventType, portfolioId);
        kafkaTemplate.send(PORTFOLIO_GROUP_CHANGED_TOPIC, userId, event);
    }
}
