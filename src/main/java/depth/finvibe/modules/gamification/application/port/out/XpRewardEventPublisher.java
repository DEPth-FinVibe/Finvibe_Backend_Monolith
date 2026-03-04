package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.common.gamification.dto.XpRewardEvent;

public interface XpRewardEventPublisher {
    void publishXpRewardEvent(XpRewardEvent xpRewardEvent);
}
