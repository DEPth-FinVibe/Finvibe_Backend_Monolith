package depth.finvibe.modules.user.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface UserEventPublisher {
    void publishUserSignUpEvent(UUID userId);
    void publishUserSignInEvent(UUID userId);
    void publishUserMetricEvent(UUID userId, String eventType, Double delta, Instant occurredAt);
}
