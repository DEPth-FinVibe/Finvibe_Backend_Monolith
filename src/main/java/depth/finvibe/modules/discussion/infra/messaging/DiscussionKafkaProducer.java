package depth.finvibe.modules.discussion.infra.messaging;

import depth.finvibe.modules.discussion.application.port.out.DiscussionEventPort;
import depth.finvibe.common.insight.dto.DiscussionEvent;
import depth.finvibe.common.insight.dto.DiscussionEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscussionKafkaProducer implements DiscussionEventPort {

    private static final String TOPIC = "discussion.dicussion-events.v1";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishCreated(Long newsId) {
        DiscussionEvent event = new DiscussionEvent(DiscussionEventType.CREATED, newsId);
        kafkaTemplate.send(TOPIC, newsId.toString(), event);
        log.info("Published DiscussionCreatedEvent to Kafka: newsId={}", newsId);
    }

    @Override
    public void publishDeleted(Long newsId) {
        DiscussionEvent event = new DiscussionEvent(DiscussionEventType.DELETED, newsId);
        kafkaTemplate.send(TOPIC, newsId.toString(), event);
        log.info("Published DiscussionDeletedEvent to Kafka: newsId={}", newsId);
    }
}
