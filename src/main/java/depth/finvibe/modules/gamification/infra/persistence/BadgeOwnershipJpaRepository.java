package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.domain.idclass.BadgeOwnershipId;

public interface BadgeOwnershipJpaRepository extends JpaRepository<BadgeOwnership, BadgeOwnershipId> {
    boolean existsByBadgeAndOwnerId(Badge badge, UUID ownerId);

    List<BadgeOwnership> findByOwnerId(UUID ownerId);
}
