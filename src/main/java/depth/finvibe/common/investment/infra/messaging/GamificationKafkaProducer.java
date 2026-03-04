package depth.finvibe.common.investment.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.RewardBadgeEvent;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class GamificationKafkaProducer implements GamificationEventProducer {
  private static final String USER_METRIC_UPDATED_TOPIC = "gamification.update-user-metric.v1";
  private static final String REWARD_BADGE_TOPIC = "gamification.reward-badge.v1";

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public void publishUserMetricUpdatedEvent(UserMetricUpdatedEvent event) {
    if (event == null || event.getUserId() == null) {
      log.warn("Skipping UserMetricUpdatedEvent publish: invalid payload");
      return;
    }
    kafkaTemplate.send(USER_METRIC_UPDATED_TOPIC, event.getUserId(), event);
  }

  @Override
  public void publishRewardBadgeEvent(RewardBadgeEvent event) {
    if (event == null || event.getUserId() == null) {
      log.warn("Skipping RewardBadgeEvent publish: invalid payload");
      return;
    }
    kafkaTemplate.send(REWARD_BADGE_TOPIC, event.getUserId(), event);
  }
}
