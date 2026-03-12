package depth.finvibe.modules.gamification.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.error.DomainException;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
@Embeddable
public class ChallengeCondition {
    @Enumerated(EnumType.STRING)
    private UserMetricType metricType;

    private Double targetValue;

    public static ChallengeCondition of(UserMetricType metricType, Double targetValue) {
        checkArgumentsAreValid(metricType, targetValue);

        return ChallengeCondition.builder()
                .metricType(metricType)
                .targetValue(targetValue)
                .build();
    }

    private static void checkArgumentsAreValid(UserMetricType metricType, Double targetValue) {
        if (metricType == null) {
            throw new DomainException(GamificationErrorCode.INVALID_METRIC_TYPE);
        }

        if (targetValue == null || targetValue <= 0) {
            throw new DomainException(GamificationErrorCode.INVALID_TARGET_VALUE);
        }
    }
}
