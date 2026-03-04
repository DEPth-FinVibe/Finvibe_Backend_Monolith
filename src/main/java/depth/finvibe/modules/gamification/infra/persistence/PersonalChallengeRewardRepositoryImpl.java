package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRewardRepository;
import depth.finvibe.modules.gamification.domain.PersonalChallengeReward;
import depth.finvibe.modules.gamification.domain.vo.Period;

@Repository
@RequiredArgsConstructor
public class PersonalChallengeRewardRepositoryImpl implements PersonalChallengeRewardRepository {
    private final PersonalChallengeRewardJpaRepository personalChallengeRewardJpaRepository;

    @Override
    public void saveAll(List<PersonalChallengeReward> rewards) {
        personalChallengeRewardJpaRepository.saveAll(rewards);
    }

    @Override
    public List<PersonalChallengeReward> findAllByUserIdAndPeriod(UUID userId, Period period) {
        return personalChallengeRewardJpaRepository.findByUserIdAndPeriodStartDateBetween(
                userId,
                period.getStartDate(),
                period.getEndDate()
        );
    }
}
