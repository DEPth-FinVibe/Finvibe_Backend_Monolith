package depth.finvibe.modules.gamification.infra.persistence;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.SquadRankingHistoryRepository;
import depth.finvibe.modules.gamification.domain.SquadRankingHistory;

@Repository
@RequiredArgsConstructor
public class SquadRankingHistoryRepositoryImpl implements SquadRankingHistoryRepository {
    private final SquadRankingHistoryJpaRepository squadRankingHistoryJpaRepository;

    @Override
    public void save(SquadRankingHistory history) {
        squadRankingHistoryJpaRepository.save(history);
    }

    @Override
    public Optional<SquadRankingHistory> findFirstBySquadIdOrderByRecordDateDescIdDesc(Long squadId) {
        return squadRankingHistoryJpaRepository.findFirstBySquadIdOrderByRecordDateDescIdDesc(squadId);
    }
}
