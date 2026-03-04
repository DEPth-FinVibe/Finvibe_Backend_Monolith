package depth.finvibe.modules.gamification.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.UserXpRankingSnapshot;
import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;

public interface UserXpRankingSnapshotRepository {
    void replaceSnapshots(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            List<UserXpRankingSnapshot> snapshots);

    List<UserXpRankingSnapshot> findTopByPeriod(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            int size);

    Optional<UserXpRankingSnapshot> findByPeriodAndUserId(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            UUID userId);
}
