package depth.finvibe.modules.gamification.domain;

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

import depth.finvibe.modules.gamification.domain.vo.ChallengeCondition;
import depth.finvibe.modules.gamification.domain.vo.Period;
import depth.finvibe.modules.gamification.domain.vo.Reward;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;
import depth.finvibe.common.error.DomainException;
import org.springframework.util.StringUtils;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuperBuilder
public class PersonalChallenge extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    @Embedded
    private ChallengeCondition condition;

    @Embedded
    private Period period;

    @Embedded
    private Reward reward;

    public static PersonalChallenge of(String title, String description, ChallengeCondition condition, Period period, Reward reward) {
        checkArgumentsAreValid(title, description, condition, period, reward);

        return PersonalChallenge.builder()
                .title(title)
                .description(description)
                .condition(condition)
                .period(period)
                .reward(reward)
                .build();
    }

    private static void checkArgumentsAreValid(String title, String description, ChallengeCondition condition, Period period, Reward reward) {
        if (!StringUtils.hasText(title)) {
            throw new DomainException(GamificationErrorCode.INVALID_PERSONAL_CHALLENGE_TITLE_IS_EMPTY);
        }
    }
}
