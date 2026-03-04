package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.modules.gamification.domain.UserSquad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSquadRepository {
    void save(UserSquad userSquad);
    Optional<UserSquad> findByUserId(UUID userId);
    List<UserSquad> findAllBySquadId(Long squadId);
}
