package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.UserSquadRepository;
import depth.finvibe.modules.gamification.domain.UserSquad;

@Repository
@RequiredArgsConstructor
public class UserSquadRepositoryImpl implements UserSquadRepository {
    private final UserSquadJpaRepository userSquadJpaRepository;

    @Override
    public void save(UserSquad userSquad) {
        userSquadJpaRepository.save(userSquad);
    }

    @Override
    public Optional<UserSquad> findByUserId(UUID userId) {
        return userSquadJpaRepository.findByUserId(userId);
    }

    @Override
    public List<UserSquad> findAllBySquadId(Long squadId) {
        return userSquadJpaRepository.findAllBySquadId(squadId);
    }
}
