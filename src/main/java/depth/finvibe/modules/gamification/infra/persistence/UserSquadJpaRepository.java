package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.UserSquad;

public interface UserSquadJpaRepository extends JpaRepository<UserSquad, UUID> {
    Optional<UserSquad> findByUserId(UUID userId);

    List<UserSquad> findAllBySquadId(Long squadId);
}
