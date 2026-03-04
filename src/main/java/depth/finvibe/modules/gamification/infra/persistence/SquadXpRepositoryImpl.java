package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.SquadXpRepository;
import depth.finvibe.modules.gamification.domain.SquadXp;

@Repository
@RequiredArgsConstructor
public class SquadXpRepositoryImpl implements SquadXpRepository {
    private final SquadXpJpaRepository squadXpJpaRepository;

    @Override
    public void save(SquadXp squadXp) {
        squadXpJpaRepository.save(squadXp);
    }

    @Override
    public void saveAll(List<SquadXp> squadXps) {
        squadXpJpaRepository.saveAll(squadXps);
    }

    @Override
    public Optional<SquadXp> findBySquadId(Long squadId) {
        return squadXpJpaRepository.findBySquadId(squadId);
    }

    @Override
    public List<SquadXp> findAllByOrderByTotalXpDesc() {
        return squadXpJpaRepository.findAllByOrderByTotalXpDesc();
    }
}
