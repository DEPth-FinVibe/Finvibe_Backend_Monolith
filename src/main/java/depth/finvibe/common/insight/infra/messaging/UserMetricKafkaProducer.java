package depth.finvibe.common.insight.infra.messaging;

import depth.finvibe.common.insight.application.port.out.UserMetricEventPort;
import depth.finvibe.common.insight.dto.MetricEventType;
import depth.finvibe.common.insight.dto.UserMetricUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMetricKafkaProducer implements UserMetricEventPort {

    private final KafkaTemplate<String, UserMetricUpdatedEvent> kafkaTemplate;

    @Value("${metric.kafka.topic:user.metric.updated.v1}")
    private String topic;

    @Override
    public void publish(String userId, MetricEventType eventType, Double delta, Instant occurredAt) {
        UserMetricUpdatedEvent event = new UserMetricUpdatedEvent(userId, eventType, delta, occurredAt);
        kafkaTemplate.send(topic, userId, event);
        log.info("Published UserMetricUpdatedEvent: userId={}, eventType={}, delta={}", userId, eventType, delta);
    }
}
