package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.modules.gamification.domain.SquadRankingHistory;

import java.util.Optional;

public interface SquadRankingHistoryRepository {
    void save(SquadRankingHistory history);
    Optional<SquadRankingHistory> findFirstBySquadIdOrderByRecordDateDescIdDesc(Long squadId);
}
