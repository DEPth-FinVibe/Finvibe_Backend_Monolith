package depth.finvibe.modules.gamification.infra.messaging;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.in.MetricEventCommandUseCase;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.dto.UserMetricUpdatedEvent;
import depth.finvibe.common.error.DomainException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMetricUpdatedEventConsumer {

    private final MetricEventCommandUseCase metricEventCommandUseCase;

    @KafkaListener(
            topics = "gamification.update-user-metric.v1",
            groupId = "gamification-group",
            properties = {
                    "spring.json.value.default.type=depth.finvibe.common.gamification.dto.UserMetricUpdatedEvent"
            }
    )
    public void consumeUserMetricUpdatedEvent(UserMetricUpdatedEvent event) {
        if (event == null || event.getUserId() == null || event.getEventType() == null) {
            log.warn("UserMetricUpdatedEvent is missing required fields");
            return;
        }

        try {
            UUID userId = UUID.fromString(event.getUserId());
            metricEventCommandUseCase.updateUserMetricByEventType(
                    event.getEventType(),
                    userId,
                    event.getDelta(),
                    event.getOccurredAt());

        } catch (IllegalArgumentException ex) {
            log.warn("UserMetricUpdatedEvent has invalid userId: {}", event.getUserId());
        } catch (DomainException ex) {
            if (ex.getErrorCode() == GamificationErrorCode.INVALID_METRIC_TYPE
                    || ex.getErrorCode() == GamificationErrorCode.INVALID_METRIC_DELTA) {
                log.warn("UserMetricUpdatedEvent has invalid event payload");
                return;
            }
            throw ex;
        }
    }
}
