package depth.finvibe.common.investment.application.port.out;

import depth.finvibe.common.investment.dto.RewardBadgeEvent;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;

public interface GamificationEventProducer {
  void publishUserMetricUpdatedEvent(UserMetricUpdatedEvent event);

  void publishRewardBadgeEvent(RewardBadgeEvent event);
}
