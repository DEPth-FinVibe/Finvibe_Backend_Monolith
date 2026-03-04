package depth.finvibe.modules.news.infra.messaging;

import depth.finvibe.common.insight.dto.DiscussionEvent;
import depth.finvibe.common.insight.dto.DiscussionEventType;
import depth.finvibe.modules.news.application.port.out.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscussionKafkaConsumer {

    private final NewsRepository newsRepository;

    @KafkaListener(
            topics = "discussion.dicussion-events.v1",
            groupId = "news-module-group",
            properties = {
                    "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                    "spring.json.value.default.type=depth.finvibe.common.insight.dto.DiscussionEvent",
                    "spring.json.trusted.packages=depth.finvibe.common.insight.dto"
            }
    )
    @Transactional
    public void handleDiscussionEvent(ConsumerRecord<String, DiscussionEvent> record) {
        try {
            log.info("Consumed DiscussionEvent from topic: {}, key: {}, offset: {}",
                    record.topic(), record.key(), record.offset());
            DiscussionEvent event = record.value();
            if (event == null || event.getNewsId() == null || event.getType() == null) {
                log.warn("Skipping invalid DiscussionEvent: {}", event);
                return;
            }

            DiscussionEventType type = event.getType();
            Long newsId = event.getNewsId();
            log.info("Received discussion event from Kafka: type={}, newsId={}", type, newsId);

            newsRepository.findById(newsId).ifPresent(news -> {
                if (type == DiscussionEventType.CREATED) {
                    news.incrementDiscussionCount();
                } else if (type == DiscussionEventType.DELETED) {
                    news.decrementDiscussionCount();
                }
                newsRepository.save(news);
                log.info("Updated discussion count for newsId={}: {}", newsId, news.getDiscussionCount());
            });
        } catch (Exception e) {
            log.error("Failed to process discussion event: {}", record, e);
        }
    }
}
