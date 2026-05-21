package depth.finvibe.modules.user.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface UserEventPublisher {
    void publishUserSignUpEvent(Long userId);
    void publishUserSignInEvent(Long userId);
    void publishUserMetricEvent(Long userId, String eventType, Double delta, Instant occurredAt);
}
