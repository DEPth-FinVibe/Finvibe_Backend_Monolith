package depth.finvibe.common.insight.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMetricUpdatedEvent {

    private String userId;
    private MetricEventType eventType;
    private Double delta;
    private Instant occurredAt;
}
