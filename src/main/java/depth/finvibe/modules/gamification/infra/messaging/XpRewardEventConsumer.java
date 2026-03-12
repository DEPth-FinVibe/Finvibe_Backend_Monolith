package depth.finvibe.modules.gamification.infra.messaging;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.in.XpCommandUseCase;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.dto.XpRewardEvent;
import depth.finvibe.common.error.DomainException;

@Slf4j
@Component
@RequiredArgsConstructor
public class XpRewardEventConsumer {

    private final XpCommandUseCase xpCommandUseCase;

    @KafkaListener(
            topics = "gamification.reward-xp.v1",
            groupId = "gamification-group",
            properties = {
                    "spring.json.value.default.type=depth.finvibe.common.gamification.dto.XpRewardEvent"
            }
    )
    public void consumeXpRewardEvent(XpRewardEvent event) {
        if (event == null || event.getUserId() == null || event.getXpAmount() == null || event.getReason() == null
                || event.getReason().isBlank()) {
            log.warn("XpRewardEvent is missing required fields");
            return;
        }

        try {
            UUID userId = UUID.fromString(event.getUserId());
            xpCommandUseCase.grantUserXp(userId, event.getXpAmount(), event.getReason());
        } catch (IllegalArgumentException ex) {
            log.warn("XpRewardEvent has invalid userId: {}", event.getUserId());
        } catch (DomainException ex) {
            if (ex.getErrorCode() == GamificationErrorCode.INVALID_XP_VALUE
                    || ex.getErrorCode() == GamificationErrorCode.INVALID_XP_REASON
                    || ex.getErrorCode() == GamificationErrorCode.INVALID_USER_ID) {
                log.warn("XpRewardEvent has invalid payload");
                return;
            }
            throw ex;
        }
    }
}
