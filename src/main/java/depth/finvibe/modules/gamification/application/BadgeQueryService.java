package depth.finvibe.modules.gamification.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import depth.finvibe.modules.gamification.application.port.in.BadgeQueryUseCase;
import depth.finvibe.modules.gamification.application.port.out.BadgeOwnershipRepository;
import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.dto.BadgeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeQueryService implements BadgeQueryUseCase {

    private final BadgeOwnershipRepository badgeOwnershipRepository;

    @Override
    public List<BadgeDto.BadgeInfo> getUserBadges(UUID userId) {
        List<BadgeOwnership> badgeOwnerships = badgeOwnershipRepository.findByUserId(userId);

        return badgeOwnerships.stream()
                .map(this::toBadgeInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<BadgeDto.BadgeStatistics> getAllBadges() {
        return Badge.getAllBadges().stream()
                .map(this::toBadgeStatistics)
                .collect(Collectors.toList());
    }


    private BadgeDto.BadgeInfo toBadgeInfo(BadgeOwnership badgeOwnership) {
        return BadgeDto.BadgeInfo.builder()
                .badge(badgeOwnership.getBadge())
                .displayName(badgeOwnership.getBadge().getDisplayName())
                .acquiredAt(badgeOwnership.getCreatedAt())
                .build();
    }

    private BadgeDto.BadgeStatistics toBadgeStatistics(Badge badge) {
        return BadgeDto.BadgeStatistics.builder()
                .badge(badge)
                .displayName(badge.getDisplayName())
                .build();
    }
}