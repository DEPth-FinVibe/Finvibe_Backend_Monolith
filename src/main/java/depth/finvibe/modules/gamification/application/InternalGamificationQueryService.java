package depth.finvibe.modules.gamification.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.gamification.application.port.in.InternalGamificationQueryUseCase;
import depth.finvibe.modules.gamification.application.port.out.BadgeOwnershipRepository;
import depth.finvibe.modules.gamification.application.port.out.MetricRepository;
import depth.finvibe.modules.gamification.application.port.out.UserXpRankingSnapshotRepository;
import depth.finvibe.modules.gamification.application.port.out.UserXpRepository;
import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.UserXp;
import depth.finvibe.modules.gamification.domain.UserXpRankingSnapshot;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.dto.InternalGamificationDto;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalGamificationQueryService implements InternalGamificationQueryUseCase {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final BadgeOwnershipRepository badgeOwnershipRepository;
    private final UserXpRepository userXpRepository;
    private final UserXpRankingSnapshotRepository userXpRankingSnapshotRepository;
    private final MetricRepository metricRepository;

    @Override
    public InternalGamificationDto.UserSummaryResponse getUserSummary(UUID userId) {
        List<BadgeOwnership> ownedBadges = badgeOwnershipRepository.findByUserId(userId);
        Long totalXp = userXpRepository.findByUserId(userId)
                .map(UserXp::getTotalXp)
                .orElse(0L);
        Integer ranking = getWeeklyRanking(userId);
        Double currentReturnRate = metricRepository.findByUserIdAndType(
                        userId,
                        UserMetricType.CURRENT_RETURN_RATE,
                        CollectPeriod.ALLTIME)
                .map(UserMetric::getValue)
                .orElse(null);

        return InternalGamificationDto.UserSummaryResponse.builder()
                .userId(userId)
                .badges(ownedBadges.stream().map(InternalGamificationDto.OwnedBadge::from).toList())
                .ranking(ranking)
                .totalXp(totalXp)
                .currentReturnRate(currentReturnRate)
                .build();
    }

    private Integer getWeeklyRanking(UUID userId) {
        LocalDate periodStartDate = getCurrentStart(RankingPeriod.WEEKLY, LocalDateTime.now(DEFAULT_ZONE)).toLocalDate();
        return userXpRankingSnapshotRepository.findByPeriodAndUserId(RankingPeriod.WEEKLY, periodStartDate, userId)
                .map(UserXpRankingSnapshot::getRanking)
                .orElse(null);
    }

    private LocalDateTime getCurrentStart(RankingPeriod rankingPeriod, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (rankingPeriod == RankingPeriod.WEEKLY) {
            return today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
        }
        return today.withDayOfMonth(1).atStartOfDay();
    }
}
