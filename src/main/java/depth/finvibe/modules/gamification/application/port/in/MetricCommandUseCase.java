package depth.finvibe.modules.gamification.application.port.in;

import java.time.Instant;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.enums.UserMetricType;

public interface MetricCommandUseCase {

    /**
     * 사용자 메트릭을 증분으로 갱신합니다.
     *
     * @param metricType 메트릭 타입
     * @param userId 사용자 ID
     * @param delta 증분 값
     * @param occurredAt 이벤트 발생 시각(UTC)
     */
    void updateUserMetric(UserMetricType metricType, UUID userId, Double delta, Instant occurredAt);

    /**
     * 주간 메트릭을 초기화합니다.
     */
    void resetWeeklyMetrics();

}
