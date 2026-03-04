package depth.finvibe.common.insight.application.port.out;

import depth.finvibe.common.insight.dto.MetricEventType;

import java.time.Instant;

public interface UserMetricEventPort {

    void publish(String userId, MetricEventType eventType, Double delta, Instant occurredAt);
}
