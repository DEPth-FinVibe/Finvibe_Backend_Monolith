package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.SquadRepository;
import depth.finvibe.modules.gamification.domain.Squad;

@Repository
@RequiredArgsConstructor
public class SquadRepositoryImpl implements SquadRepository {
    private final SquadJpaRepository squadJpaRepository;

    @Override
    public void save(Squad squad) {
        squadJpaRepository.save(squad);
    }

    @Override
    public void delete(Squad squad) {
        squadJpaRepository.delete(squad);
    }

    @Override
    public List<Squad> findAll() {
        return squadJpaRepository.findAll();
    }

    @Override
    public Optional<Squad> findById(Long id) {
        return squadJpaRepository.findById(id);
    }
}
