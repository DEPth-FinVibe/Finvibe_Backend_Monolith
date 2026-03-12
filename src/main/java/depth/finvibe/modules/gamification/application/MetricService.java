package depth.finvibe.modules.gamification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.gamification.application.port.in.BadgeCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.MetricCommandUseCase;
import depth.finvibe.modules.gamification.application.port.out.BadgeOwnershipRepository;
import depth.finvibe.modules.gamification.application.port.out.MetricRepository;
import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.error.DomainException;

@Service
@RequiredArgsConstructor
public class MetricService implements MetricCommandUseCase {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private static final double KNOWLEDGE_SEEKER_TARGET = 3.0;
    private static final double DIVERSIFICATION_MASTER_TARGET = 5.0;
    private static final double DILIGENT_INVESTOR_TARGET = 7.0;
    private static final double CHALLENGE_MARATHONER_TARGET = 10.0;

    private final MetricRepository metricRepository;
    private final BadgeOwnershipRepository badgeOwnershipRepository;
    private final BadgeCommandUseCase badgeCommandUseCase;

    @Override
    @Transactional
    public void updateUserMetric(UserMetricType metricType, UUID userId, Double delta, Instant occurredAt) {
        if (metricType == null) {
            throw new DomainException(GamificationErrorCode.INVALID_METRIC_TYPE);
        }

        if (metricType == UserMetricType.LAST_LOGIN_DATETIME) {
            updateLoginStreak(userId, occurredAt);
            return;
        }

        if (isAbsoluteMetric(metricType)) {
            if (delta == null) {
                throw new DomainException(GamificationErrorCode.INVALID_METRIC_DELTA);
            }
            saveMetric(userId, metricType, CollectPeriod.ALLTIME, delta);
            evaluateBadgeByMetric(userId, metricType, delta);
            return;
        }

        double increase = delta == null ? 0.0 : delta;
        double updatedValue = getMetricValue(userId, metricType, CollectPeriod.ALLTIME) + increase;
        saveMetric(userId, metricType, CollectPeriod.ALLTIME, updatedValue);
        updateWeeklyMetric(userId, metricType, increase);
        evaluateBadgeByMetric(userId, metricType, updatedValue);
    }

    @Override
    @Transactional
    public void resetWeeklyMetrics() {
        metricRepository.deleteAllByCollectPeriod(CollectPeriod.WEEKLY);
    }

    private void updateLoginStreak(UUID userId, Instant occurredAt) {
        if (occurredAt == null) {
            return;
        }

        LocalDate currentDate = occurredAt.atZone(DEFAULT_ZONE).toLocalDate();
        LocalDate lastLoginDate = getLastLoginDate(userId);

        saveMetric(userId, UserMetricType.LAST_LOGIN_DATETIME, CollectPeriod.ALLTIME, (double) occurredAt.toEpochMilli());

        if (lastLoginDate != null && lastLoginDate.isEqual(currentDate)) {
            return;
        }

        double currentStreak = getMetricValue(userId, UserMetricType.LOGIN_STREAK_DAYS, CollectPeriod.ALLTIME);
        double nextStreak = 1.0;
        if (lastLoginDate != null && lastLoginDate.plusDays(1).isEqual(currentDate)) {
            nextStreak = currentStreak + 1.0;
        }

        saveMetric(userId, UserMetricType.LOGIN_STREAK_DAYS, CollectPeriod.ALLTIME, nextStreak);
        evaluateBadgeByMetric(userId, UserMetricType.LOGIN_STREAK_DAYS, nextStreak);
    }

    private LocalDate getLastLoginDate(UUID userId) {
        return metricRepository.findByUserIdAndType(userId, UserMetricType.LAST_LOGIN_DATETIME, CollectPeriod.ALLTIME)
                .map(UserMetric::getValue)
                .filter(value -> value != null)
                .map(value -> Instant.ofEpochMilli(value.longValue()).atZone(DEFAULT_ZONE).toLocalDate())
                .orElse(null);
    }

    private double getMetricValue(UUID userId, UserMetricType metricType, CollectPeriod collectPeriod) {
        return metricRepository.findByUserIdAndType(userId, metricType, collectPeriod)
                .map(UserMetric::getValue)
                .orElse(0.0);
    }

    private void updateWeeklyMetric(UUID userId, UserMetricType metricType, double increase) {
        if (!isWeeklyMetric(metricType) || increase == 0.0) {
            return;
        }

        double updatedValue = getMetricValue(userId, metricType, CollectPeriod.WEEKLY) + increase;
        metricRepository.save(UserMetric.builder()
                .userId(userId)
                .type(metricType)
                .collectPeriod(CollectPeriod.WEEKLY)
                .value(updatedValue)
                .build());
    }

    private void saveMetric(UUID userId, UserMetricType metricType, CollectPeriod collectPeriod, double value) {
        metricRepository.save(UserMetric.builder()
                .userId(userId)
                .type(metricType)
                .collectPeriod(collectPeriod)
                .value(value)
                .build());
    }

    private boolean isWeeklyMetric(UserMetricType metricType) {
        return metricType != null && metricType.isWeeklyCollect();
    }

    private boolean isAbsoluteMetric(UserMetricType metricType) {
        return metricType == UserMetricType.CURRENT_RETURN_RATE
                || metricType == UserMetricType.PORTFOLIO_COUNT_WITH_STOCKS
                || metricType == UserMetricType.HOLDING_STOCK_COUNT;
    }

    private void evaluateBadgeByMetric(UUID userId, UserMetricType metricType, double value) {
        if (metricType == UserMetricType.AI_CONTENT_COMPLETE_COUNT && value >= KNOWLEDGE_SEEKER_TARGET) {
            grantBadgeIfAbsent(userId, Badge.KNOWLEDGE_SEEKER);
        }

        if (metricType == UserMetricType.HOLDING_STOCK_COUNT && value >= DIVERSIFICATION_MASTER_TARGET) {
            grantBadgeIfAbsent(userId, Badge.DIVERSIFICATION_MASTER);
        }

        if (metricType == UserMetricType.LOGIN_STREAK_DAYS && value >= DILIGENT_INVESTOR_TARGET) {
            grantBadgeIfAbsent(userId, Badge.DILIGENT_INVESTOR);
        }

        if (metricType == UserMetricType.CHALLENGE_COMPLETION_COUNT && value >= CHALLENGE_MARATHONER_TARGET) {
            grantBadgeIfAbsent(userId, Badge.CHALLENGE_MARATHONER);
        }
    }

    private void grantBadgeIfAbsent(UUID userId, Badge badge) {
        BadgeOwnership ownership = BadgeOwnership.of(badge, userId);
        if (badgeOwnershipRepository.isExist(ownership)) {
            return;
        }
        badgeCommandUseCase.grantBadgeToUser(userId, badge);
    }
}
