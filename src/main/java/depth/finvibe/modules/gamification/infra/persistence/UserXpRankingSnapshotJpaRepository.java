package depth.finvibe.modules.gamification.infra.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import depth.finvibe.modules.gamification.domain.UserXpRankingSnapshot;
import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;

public interface UserXpRankingSnapshotJpaRepository extends JpaRepository<UserXpRankingSnapshot, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from UserXpRankingSnapshot snapshot
            where snapshot.periodType = :periodType
              and snapshot.periodStartDate = :periodStartDate
            """)
    int deleteByPeriodTypeAndPeriodStartDate(
            @Param("periodType") RankingPeriod periodType,
            @Param("periodStartDate") LocalDate periodStartDate);

    List<UserXpRankingSnapshot> findByPeriodTypeAndPeriodStartDateOrderByRankingAsc(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            Pageable pageable);

    Optional<UserXpRankingSnapshot> findByPeriodTypeAndPeriodStartDateAndUserId(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            UUID userId);
}
