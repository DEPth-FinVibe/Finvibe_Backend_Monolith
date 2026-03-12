package depth.finvibe.modules.gamification.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.error.DomainException;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
@Embeddable
public class Reward {
    private Long rewardXp;

    @Enumerated(EnumType.STRING)
    private Badge rewardBadge;

    public static Reward of(Long rewardXp, Badge rewardBadge) {
        checkArgumentsAreValid(rewardXp, rewardBadge);

        return Reward.builder()
                .rewardXp(rewardXp)
                .rewardBadge(rewardBadge)
                .build();
    }

    private static void checkArgumentsAreValid(Long rewardXp, Badge rewardBadge) {
        if (rewardXp == null && rewardBadge == null) {
            throw new DomainException(GamificationErrorCode.INVALID_REWARD_XP);
        }
        if (rewardXp != null && rewardXp <= 0) {
            throw new DomainException(GamificationErrorCode.INVALID_REWARD_XP);
        }
    }
}
