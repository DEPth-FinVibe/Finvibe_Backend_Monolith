package depth.finvibe.modules.gamification.domain;

import java.util.UUID;

import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.modules.gamification.domain.vo.Period;
import depth.finvibe.modules.gamification.domain.vo.Reward;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuperBuilder
public class PersonalChallengeReward extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long challengeId;

    private UUID userId;

    @Embedded
    private Period period;

    @Embedded
    private Reward reward;

    public static PersonalChallengeReward of(Long challengeId, UUID userId, Period period, Reward reward) {

        checkArgumentsAreValid(challengeId, userId, period, reward);

        return PersonalChallengeReward.builder()
                .challengeId(challengeId)
                .userId(userId)
                .period(period)
                .reward(reward)
                .build();
    }

    private static void checkArgumentsAreValid(Long challengeId, UUID userId, Period period, Reward reward) {
        if (challengeId == null || challengeId <= 0) {
            throw new DomainException(GamificationErrorCode.INVALID_PERSONAL_CHALLENGE_ID);
        }

        if (userId == null) {
            throw new DomainException(GamificationErrorCode.INVALID_USER_ID);
        }

        if (period == null) {
            throw new DomainException(GamificationErrorCode.INVALID_PERIOD);
        }

        if (reward == null) {
            throw new DomainException(GamificationErrorCode.INVALID_REWARD);
        }
    }
}
