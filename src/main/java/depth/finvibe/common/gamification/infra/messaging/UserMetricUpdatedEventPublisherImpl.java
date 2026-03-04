package depth.finvibe.common.gamification.infra.messaging;

import lombok.RequiredArgsConstructor;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import depth.finvibe.common.gamification.dto.UserMetricUpdatedEvent;
import depth.finvibe.common.gamification.messaging.UserMetricUpdatedEventPublisher;

@Component
@RequiredArgsConstructor
public class UserMetricUpdatedEventPublisherImpl implements UserMetricUpdatedEventPublisher {
    private static final String UPDATE_USER_METRIC_TOPIC = "gamification.update-user-metric.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishUserMetricUpdatedEvent(UserMetricUpdatedEvent event) {
        kafkaTemplate.send(UPDATE_USER_METRIC_TOPIC, event);
    }
}
