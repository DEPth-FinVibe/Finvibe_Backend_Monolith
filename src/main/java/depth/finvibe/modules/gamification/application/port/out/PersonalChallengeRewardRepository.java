package depth.finvibe.modules.gamification.application.port.out;

import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.PersonalChallengeReward;
import depth.finvibe.modules.gamification.domain.vo.Period;

public interface PersonalChallengeRewardRepository {
    void saveAll(List<PersonalChallengeReward> rewards);

    List<PersonalChallengeReward> findAllByUserIdAndPeriod(UUID userId, Period period);
}
