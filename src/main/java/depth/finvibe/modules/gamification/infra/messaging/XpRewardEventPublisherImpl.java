package depth.finvibe.modules.gamification.infra.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.out.XpRewardEventPublisher;
import depth.finvibe.common.gamification.dto.XpRewardEvent;

@Component("gamificationXpRewardEventPublisher")
@RequiredArgsConstructor
public class XpRewardEventPublisherImpl implements XpRewardEventPublisher {
    private static final String REWARD_XP_TOPIC = "gamification.reward-xp.v1";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishXpRewardEvent(XpRewardEvent xpRewardEvent) {
        kafkaTemplate.send(REWARD_XP_TOPIC, xpRewardEvent);
    }
}
