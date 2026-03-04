package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRepository;
import depth.finvibe.modules.gamification.domain.PersonalChallenge;
import depth.finvibe.modules.gamification.domain.vo.Period;

@Repository
@RequiredArgsConstructor
public class PersonalChallengeRepositoryImpl implements PersonalChallengeRepository {
    private final PersonalChallengeJpaRepository personalChallengeJpaRepository;

    @Override
    public void save(PersonalChallenge personalChallenge) {
        personalChallengeJpaRepository.save(personalChallenge);
    }

    @Override
    public void saveAll(List<PersonalChallenge> personalChallenges) {
        personalChallengeJpaRepository.saveAll(personalChallenges);
    }

    @Override
    public List<PersonalChallenge> findAllByPeriod(Period period) {
        return personalChallengeJpaRepository.findByPeriodStartDateAndPeriodEndDate(
                period.getStartDate(),
                period.getEndDate()
        );
    }

    @Override
    public boolean existsByPeriod(Period period) {
        return personalChallengeJpaRepository.existsByPeriodStartDateAndPeriodEndDate(
                period.getStartDate(),
                period.getEndDate()
        );
    }

    @Override
    public List<PersonalChallenge> findAllByIds(List<Long> ids) {
        return personalChallengeJpaRepository.findAllById(ids);
    }
}
