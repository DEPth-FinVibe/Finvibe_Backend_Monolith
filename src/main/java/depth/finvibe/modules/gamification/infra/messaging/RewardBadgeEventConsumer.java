package depth.finvibe.modules.gamification.infra.messaging;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.in.BadgeCommandUseCase;
import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.dto.RewardBadgeEvent;
import depth.finvibe.common.error.DomainException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardBadgeEventConsumer {

    private final BadgeCommandUseCase badgeCommandUseCase;

    @KafkaListener(
            topics = "gamification.reward-badge.v1",
            groupId = "gamification-group",
            properties = {
                    "spring.json.value.default.type=depth.finvibe.common.gamification.dto.RewardBadgeEvent"
            }
    )
    public void consumeRewardBadgeEvent(RewardBadgeEvent event) {
        if (event == null || event.getUserId() == null || event.getBadgeCode() == null) {
            log.warn("RewardBadgeEvent is missing required fields");
            return;
        }

        try {
            UUID userId = UUID.fromString(event.getUserId());
            Badge badge = Badge.valueOf(event.getBadgeCode());
            badgeCommandUseCase.grantBadgeToUser(userId, badge);
        } catch (IllegalArgumentException ex) {
            log.warn("RewardBadgeEvent has invalid data: userId={}, badgeCode={}",
                    event.getUserId(), event.getBadgeCode());
        } catch (DomainException ex) {
            if (ex.getErrorCode() == GamificationErrorCode.BADGE_ALREADY_EXIST) {
                return;
            }
            throw ex;
        }
    }
}
