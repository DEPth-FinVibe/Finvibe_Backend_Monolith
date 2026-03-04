package depth.finvibe.modules.gamification.application.port.out;

import java.util.List;

import depth.finvibe.modules.gamification.domain.PersonalChallenge;
import depth.finvibe.modules.gamification.domain.vo.Period;

public interface PersonalChallengeRepository {
    void save(PersonalChallenge personalChallenge);

    void saveAll(List<PersonalChallenge> personalChallenges);

    List<PersonalChallenge> findAllByPeriod(Period period);

    boolean existsByPeriod(Period period);

    List<PersonalChallenge> findAllByIds(List<Long> ids);
}
