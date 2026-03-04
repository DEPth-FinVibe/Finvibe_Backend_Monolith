package depth.finvibe.modules.gamification.application;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import depth.finvibe.modules.gamification.application.port.in.MetricCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.MetricEventCommandUseCase;
import depth.finvibe.modules.gamification.domain.enums.MetricEventType;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.error.DomainException;

@Service
@RequiredArgsConstructor
public class MetricEventService implements MetricEventCommandUseCase {

    private final MetricCommandUseCase metricCommandUseCase;

    @Override
    public void updateUserMetricByEventType(MetricEventType eventType, UUID userId, Double delta, Instant occurredAt) {
        if (eventType == null) {
            throw new DomainException(GamificationErrorCode.INVALID_METRIC_TYPE);
        }

        switch (eventType) {
            case LOGIN -> {
                metricCommandUseCase.updateUserMetric(
                        UserMetricType.LOGIN_COUNT_PER_DAY,
                        userId,
                        getOrDefaultDelta(delta, 1.0),
                        occurredAt);
                metricCommandUseCase.updateUserMetric(
                        UserMetricType.LAST_LOGIN_DATETIME,
                        userId,
                        null,
                        occurredAt);
            }
            case AI_CONTENT_COMPLETED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.AI_CONTENT_COMPLETE_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case HOLDING_STOCK_COUNT_CHANGED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.HOLDING_STOCK_COUNT,
                    userId,
                    requireDelta(delta),
                    occurredAt);
            case CHALLENGE_COMPLETED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.CHALLENGE_COMPLETION_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case CURRENT_RETURN_RATE_UPDATED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.CURRENT_RETURN_RATE,
                    userId,
                    requireDelta(delta),
                    occurredAt);
            case STOCK_BOUGHT -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.STOCK_BUY_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case STOCK_SOLD -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.STOCK_SELL_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case PORTFOLIO_WITH_STOCKS_COUNT_CHANGED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.PORTFOLIO_COUNT_WITH_STOCKS,
                    userId,
                    requireDelta(delta),
                    occurredAt);
            case NEWS_COMMENT_CREATED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.NEWS_COMMENT_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case NEWS_LIKED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.NEWS_LIKE_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case DISCUSSION_POST_CREATED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.DISCUSSION_POST_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case DISCUSSION_COMMENT_CREATED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.DISCUSSION_COMMENT_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
            case DISCUSSION_LIKED -> metricCommandUseCase.updateUserMetric(
                    UserMetricType.DISCUSSION_LIKE_COUNT,
                    userId,
                    getOrDefaultDelta(delta, 1.0),
                    occurredAt);
        }
    }

    private double getOrDefaultDelta(Double delta, double defaultValue) {
        return delta == null ? defaultValue : delta;
    }

    private double requireDelta(Double delta) {
        if (delta == null) {
            throw new DomainException(GamificationErrorCode.INVALID_METRIC_DELTA);
        }
        return delta;
    }
}
