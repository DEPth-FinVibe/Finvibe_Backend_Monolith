package depth.finvibe.modules.gamification.infra.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.PersonalChallengeReward;

public interface PersonalChallengeRewardJpaRepository extends JpaRepository<PersonalChallengeReward, Long> {
    List<PersonalChallengeReward> findByUserIdAndPeriodStartDateBetween(UUID userId, LocalDate startDate, LocalDate endDate);
}
