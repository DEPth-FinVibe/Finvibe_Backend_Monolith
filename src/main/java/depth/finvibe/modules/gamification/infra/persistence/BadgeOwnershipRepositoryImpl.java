package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.UUID;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.BadgeOwnershipRepository;
import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.QBadgeOwnership;
import depth.finvibe.modules.gamification.domain.enums.Badge;

@Repository
@RequiredArgsConstructor
public class BadgeOwnershipRepositoryImpl implements BadgeOwnershipRepository {
    private final BadgeOwnershipJpaRepository badgeOwnershipJpaRepository;

    @Override
    public void save(BadgeOwnership badgeOwnership) {
        badgeOwnershipJpaRepository.save(badgeOwnership);
    }

    @Override
    public boolean isExist(BadgeOwnership badgeOwnership) {
        return badgeOwnershipJpaRepository.existsByBadgeAndOwnerId(
            badgeOwnership.getBadge(),
            badgeOwnership.getOwnerId()
        );
    }

    @Override
    public List<BadgeOwnership> findByUserId(UUID userId) {
        return badgeOwnershipJpaRepository.findByOwnerId(userId);
    }
}
