package depth.finvibe.common.investment.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMetricUpdatedEvent {
  private String userId;
  private MetricEventType eventType;
  private Double delta;
  private Instant occurredAt;
}
