package depth.finvibe.modules.study.infra.messaging;

import depth.finvibe.modules.study.application.port.out.XpRewardEventPublisher;
import depth.finvibe.common.gamification.dto.XpRewardEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component("studyXpRewardEventPublisher")
@RequiredArgsConstructor
public class XpRewardEventPublisherImpl implements XpRewardEventPublisher {
    private static final String REWARD_XP_TOPIC = "gamification.reward-xp.v1";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishXpRewardEvent(XpRewardEvent xpRewardEvent) {
        kafkaTemplate.send(REWARD_XP_TOPIC, xpRewardEvent);
    }
}
