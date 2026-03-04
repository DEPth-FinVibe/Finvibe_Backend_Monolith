package depth.finvibe.modules.gamification.infra.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.PersonalChallenge;

public interface PersonalChallengeJpaRepository extends JpaRepository<PersonalChallenge, Long> {
    List<PersonalChallenge> findByPeriodStartDateAndPeriodEndDate(LocalDate startDate, LocalDate endDate);

    boolean existsByPeriodStartDateAndPeriodEndDate(LocalDate startDate, LocalDate endDate);
}
