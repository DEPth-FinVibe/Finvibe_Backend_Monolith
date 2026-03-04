package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.UserXp;

public interface UserXpJpaRepository extends JpaRepository<UserXp, UUID> {
    Optional<UserXp> findByUserId(UUID userId);

    List<UserXp> findAllByUserIdInOrderByWeeklyXpDesc(List<UUID> userIds);

    List<UserXp> findAllByUserIdIn(List<UUID> userIds);
}
