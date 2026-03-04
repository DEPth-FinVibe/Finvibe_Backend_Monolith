package depth.finvibe.common.gamification.messaging;

import depth.finvibe.common.gamification.dto.UserMetricUpdatedEvent;

public interface UserMetricUpdatedEventPublisher {
    void publishUserMetricUpdatedEvent(UserMetricUpdatedEvent event);
}
