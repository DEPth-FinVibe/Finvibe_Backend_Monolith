package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.common.gamification.dto.XpRewardEvent;

public interface XpRewardEventPublisher {
    void publishXpRewardEvent(XpRewardEvent xpRewardEvent);
}
