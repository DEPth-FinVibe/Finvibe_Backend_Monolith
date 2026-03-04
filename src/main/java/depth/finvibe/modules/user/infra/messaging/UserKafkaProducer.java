package depth.finvibe.modules.user.infra.messaging;

import depth.finvibe.modules.user.application.port.out.UserEventPublisher;
import depth.finvibe.common.user.dto.SignInEvent;
import depth.finvibe.common.user.dto.SignUpEvent;
import depth.finvibe.common.user.dto.UserMetricUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserKafkaProducer implements UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String USER_SIGNUP_TOPIC = "user.signup.v1";
    private static final String USER_SIGNIN_TOPIC = "user.signin.v1";
    private static final String USER_METRIC_TOPIC = "gamification.update-user-metric.v1";

    @Override
    public void publishUserSignUpEvent(UUID userId) {
        log.info("Publishing user signup event for userId: {}", userId);

        SignUpEvent signUpEvent = createSignUpEvent(userId);
        kafkaTemplate.send(USER_SIGNUP_TOPIC, userId.toString(), signUpEvent);
    }

    @Override
    public void publishUserSignInEvent(UUID userId) {
        log.info("Publishing user signin event for userId: {}", userId);

        SignInEvent signInEvent = createSignInEvent(userId);
        kafkaTemplate.send(USER_SIGNIN_TOPIC, userId.toString(), signInEvent);
    }

    private SignUpEvent createSignUpEvent(UUID userId) {
        return SignUpEvent.builder()
                .userId(userId.toString())
                .build();
    }

    private SignInEvent createSignInEvent(UUID userId) {
        return SignInEvent.builder()
                .userId(userId.toString())
                .build();
    }

    @Override
    public void publishUserMetricEvent(UUID userId, String eventType, Double delta, Instant occurredAt) {
        log.info("Publishing user metric event for userId: {}, eventType: {}", userId, eventType);

        UserMetricUpdatedEvent metricEvent = createUserMetricEvent(userId, eventType, delta, occurredAt);
        kafkaTemplate.send(USER_METRIC_TOPIC, userId.toString(), metricEvent);
    }

    private UserMetricUpdatedEvent createUserMetricEvent(UUID userId, String eventType, Double delta, Instant occurredAt) {
        return UserMetricUpdatedEvent.builder()
                .userId(userId.toString())
                .eventType(eventType)
                .delta(delta)
                .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                .build();
    }
}
